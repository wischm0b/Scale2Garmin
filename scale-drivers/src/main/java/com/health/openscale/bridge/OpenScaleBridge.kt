package com.health.openscale.bridge

import android.bluetooth.le.ScanResult
import android.content.Context
import com.health.openscale.core.bluetooth.*
import com.health.openscale.core.bluetooth.data.*
import com.health.openscale.core.bluetooth.scales.*
import com.health.openscale.core.data.*
import com.health.openscale.core.service.ScannedDeviceInfo
import kotlinx.coroutines.*
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date

/** Thin boundary between the native Scale2Garmin UI/storage and pinned openScale protocols. */
class OpenScaleBridge(private val context:Context,private val listener:Listener):AutoCloseable {
    data class Match(@JvmField val id:String,@JvmField val label:String,@JvmField val linkMode:String)
    data class Reading(@JvmField val user:Int,@JvmField val unixSeconds:Long,@JvmField val weight:Double,
        @JvmField val fat:Double,@JvmField val water:Double,@JvmField val musclePercent:Double,@JvmField val bone:Double,@JvmField val basal:Double)
    interface Listener {
        fun reading(value:Reading)
        fun status(value:String)
        fun connected()
        fun ended()
        fun interaction(labels:Array<String>?,consent:Boolean)
    }
    companion object {
        private val candidates by lazy { DriverCatalog.createHandlers() }
        private fun id(index:Int)=candidates[index].javaClass.simpleName+"_"+index
        @JvmStatic fun identify(result:ScanResult):Match? {
            val r=result.scanRecord
            val device=ScannedDeviceInfo(r?.deviceName?:"",result.device.address,result.rssi,
                r?.serviceUuids?.map{it.uuid}?:emptyList(),r?.manufacturerSpecificData,
                r?.serviceData?.mapKeys{it.key.uuid}?:emptyMap())
            return identifyDevice(device)
        }
        @JvmStatic fun identifyPaired(name:String,address:String):Match?=identifyDevice(ScannedDeviceInfo(name,address,0,emptyList(),null))
        private fun identifyDevice(device:ScannedDeviceInfo):Match? {
            candidates.forEachIndexed { index,h -> val support=runCatching {h.supportFor(device)}.getOrNull();if(support!=null)return Match(id(index),support.displayName,support.linkMode.name) }
            return null
        }
        @JvmStatic fun handlerCount()=candidates.size
        @JvmStatic fun isMedisana(id:String?)=id==null||id.isEmpty()||id.startsWith("MedisanaBs44xHandler")
    }
    private val scope=CoroutineScope(Dispatchers.Main.immediate+SupervisorJob())
    private var adapter:ModernScaleAdapter?=null
    private var pending:BluetoothEvent.UserInteractionRequired?=null
    private var userId=1
    fun connect(driverId:String,address:String){
        val p=context.getSharedPreferences("settings",0)
        userId=p.getInt("user",1)
        val height=p.getFloat("profileHeight.$userId",0f)
        val birthday=LocalDate.parse(p.getString("profileBirthday.$userId", ""))
        require(height in 80f..250f && birthday.isBefore(LocalDate.now())) { "Please complete your scale profile in Settings." }
        val user=ScaleUser(id=userId,userName=p.getString("profileName.$userId","User")?:"User",
            birthday=Date.from(birthday.atStartOfDay(ZoneId.systemDefault()).toInstant()),bodyHeight=height,
            gender=if(p.getBoolean("profileMale.$userId",true))GenderType.MALE else GenderType.FEMALE,
            activityLevel=ActivityLevel.fromInt(p.getInt("profileActivity.$userId",0)))
        val index=candidates.indices.firstOrNull{id(it)==driverId}?:error("Unknown scale driver")
        val handler=DriverCatalog.createHandlers()[index]
        // Link mode is saved from discovery, where full advertisement data is available.
        val mode=p.getString("link.$address","CONNECT_GATT")
        val data=object:DriverData {override fun user()=user;override fun lastMeasurement():ScaleMeasurement?=null}
        val communicator=when(mode){
            "BROADCAST_ONLY"->BroadcastScaleAdapter(context,data,handler)
            "CLASSIC_SPP"->SppScaleAdapter(context,data,handler)
            else->GattScaleAdapter(context,data,handler)
        }
        adapter=communicator
        scope.launch(start=CoroutineStart.UNDISPATCHED){communicator.getEventsFlow().collect{event->
            when(event){
                is BluetoothEvent.MeasurementReceived->{
                    val m=event.measurement
                    fun value(key:MeasurementType.Key<*>)=(m.values[key] as? UnitValue)?.value?.toDouble()?:Double.NaN
                    val w=value(MeasurementType.WEIGHT)
                    if(w.isFinite()&&w>0)listener.reading(Reading(if(m.userId==255||m.userId<0)userId else m.userId,
                        (m.dateTime?.time?:System.currentTimeMillis())/1000,w,value(MeasurementType.BODY_FAT),value(MeasurementType.WATER),value(MeasurementType.MUSCLE),value(MeasurementType.BONE),value(MeasurementType.BMR)))
                }
                is BluetoothEvent.Connected->listener.connected()
                is BluetoothEvent.Listening->listener.connected()
                is BluetoothEvent.DeviceMessage->listener.status(event.message)
                is BluetoothEvent.Error->listener.status(event.error)
                is BluetoothEvent.ConnectionFailed->{listener.status(event.error);listener.ended()}
                is BluetoothEvent.Disconnected->listener.ended()
                is BluetoothEvent.BroadcastComplete->listener.ended()
                is BluetoothEvent.UserInteractionRequired->{pending=event;val pair=event.data as? Pair<*,*>;val labels=pair?.first as? Array<*>;listener.interaction(labels?.map{it.toString()}?.toTypedArray(),event.interactionType==BluetoothEvent.UserInteractionType.ENTER_CONSENT)}
            }
        }}
        communicator.connect(address,user)
    }
    fun feedback(index:Int,consent:String?){
        val event=pending?:return;pending=null
        val pair=event.data as? Pair<*,*>
        val values=pair?.second
        val feedback:Any=if(consent!=null){val code=consent.toIntOrNull()?:return;val slots=event.data as? IntArray;if(slots!=null&&slots.isNotEmpty())intArrayOf(slots[0],code) else code}
            else when(values){is IntArray->values[index];is List<*>->values[index]!!;is Array<*>->values[index]!!;else->index}
        scope.launch{adapter?.processUserInteractionFeedback(event.interactionType,userId,feedback)}
    }
    override fun close(){adapter?.close();adapter=null;scope.cancel()}
}
