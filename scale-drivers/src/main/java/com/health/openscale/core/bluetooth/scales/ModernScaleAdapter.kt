/* Scale2Garmin integration for openScale drivers. GPL-3.0-or-later.
 * Protocol adapters and handlers: Copyright (C) 2025 olie.xdev.
 * Uses native app settings and snapshots instead of the openScale database/facades. */
package com.health.openscale.core.bluetooth.scales

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.health.openscale.core.bluetooth.*
import com.health.openscale.core.bluetooth.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

interface DriverData {
    fun user():ScaleUser
    fun lastMeasurement():ScaleMeasurement?
}
class FacadeDriverSettings(context:Context, namespace:String):ScaleDeviceHandler.DriverSettings {
    private val prefs=context.getSharedPreferences("driver-settings-$namespace",0)
    override fun getInt(key:String,default:Int)=prefs.getInt(key,default)
    override fun putInt(key:String,value:Int){prefs.edit().putInt(key,value).apply()}
    override fun getString(key:String,default:String?)=prefs.getString(key,default)
    override fun putString(key:String,value:String){prefs.edit().putString(key,value).apply()}
    override fun remove(key:String){prefs.edit().remove(key).apply()}
}
abstract class ModernScaleAdapter(
    protected val context:Context, private val data:DriverData, protected val handler:ScaleDeviceHandler
):ScaleCommunicator,AutoCloseable {
    protected val TAG=javaClass.simpleName
    protected val scope=CoroutineScope(Dispatchers.Main.immediate+SupervisorJob())
    protected val mainHandler=Handler(Looper.getMainLooper())
    protected var targetAddress:String?=null
    protected var lastDisconnectAtMs=0L
    val _events=MutableSharedFlow<BluetoothEvent>(extraBufferCapacity=256)
    override fun getEventsFlow():SharedFlow<BluetoothEvent> = _events.asSharedFlow()
    protected val _isConnecting=MutableStateFlow(false)
    override val isConnecting:StateFlow<Boolean> = _isConnecting.asStateFlow()
    protected val _isConnected=MutableStateFlow(false)
    override val isConnected:StateFlow<Boolean> = _isConnected.asStateFlow()
    protected var selectedUserSnapshot:ScaleUser?=data.user()
    protected val driverSettings=FacadeDriverSettings(context,handler.javaClass.simpleName+"-"+context.getSharedPreferences("settings",0).getString("address",""))
    init { handler.attachSettings(driverSettings) }
    final override fun connect(address:String,scaleUser:ScaleUser?){
        targetAddress=address;selectedUserSnapshot=scaleUser?:data.user();_isConnecting.value=true
        scope.launch {try{doConnect(address,selectedUserSnapshot!!)}catch(e:Exception){_isConnecting.value=false;_events.emit(BluetoothEvent.ConnectionFailed(address,e.message?:"Connection failed"))}}
    }
    final override fun disconnect(){doDisconnect();cleanup()}
    override fun requestMeasurement(){}
    override suspend fun processUserInteractionFeedback(interactionType:BluetoothEvent.UserInteractionType,appUserId:Int,feedbackData:Any){handler.onUserInteractionFeedback(interactionType,appUserId,feedbackData)}
    protected abstract fun doConnect(address:String,selectedUser:ScaleUser)
    protected abstract fun doDisconnect()
    protected val appCallbacks=object:ScaleDeviceHandler.Callbacks {
        override fun onPublish(measurement:ScaleMeasurement){_events.tryEmit(BluetoothEvent.MeasurementReceived(measurement.snapshot(),targetAddress?:""))}
        override fun onInfo(resId:Int,vararg args:Any){_events.tryEmit(BluetoothEvent.DeviceMessage(resolveString(resId,*args),targetAddress?:""))}
        override fun onWarn(resId:Int,vararg args:Any){onInfo(resId,*args)}
        override fun onError(resId:Int,t:Throwable?,vararg args:Any){_events.tryEmit(BluetoothEvent.Error(targetAddress?:"",resolveString(resId,*args)))}
        override fun onUserInteractionRequired(interactionType:BluetoothEvent.UserInteractionType,data:Any?){_events.tryEmit(BluetoothEvent.UserInteractionRequired(targetAddress?:"",data,interactionType))}
        override fun resolveString(resId:Int,vararg args:Any)=context.getString(resId,*args)
    }
    protected val dataProvider=object:ScaleDeviceHandler.DataProvider {
        override fun currentUser()=selectedUserSnapshot?:data.user()
        override fun usersForDevice()=listOf(currentUser())
        override fun lastMeasurementFor(userId:Int)=if(userId==currentUser().id)data.lastMeasurement() else null
    }
    protected fun cleanup(){_isConnected.value=false;_isConnecting.value=false}
    override fun close(){disconnect();handler.handleDisconnected();handler.detach();scope.cancel();mainHandler.removeCallbacksAndMessages(null)}
    fun ByteArray.toHexPreview(limit:Int):String="[${size} bytes]"
}
