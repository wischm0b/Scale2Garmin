from pathlib import Path
import re, shutil, xml.etree.ElementTree as ET
src=Path('.tools/openscale/openScale-ac5753b062112e4d74772e6b50b084cd68c59175/android_app/app/src/main')
out=Path('scale-drivers/src/main'); base=out/'java/com/health/openscale'
if out.exists():
 raise SystemExit('Initial importer only: scale-drivers already exists. Preserve the integration changes described in UPSTREAM.md.')
def write(rel,s):
 p=base/rel;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(s,encoding='utf-8')
def block(s,at):
 a=s.index('{',at);n=1;i=a+1
 while n:
  if s[i]=='{':n+=1
  elif s[i]=='}':n-=1
  i+=1
 return i
license=(src/'java/com/health/openscale/core/bluetooth/ScaleCommunicator.kt').read_text().split('package ')[0]
for p in (src/'java/com/health/openscale/core/bluetooth').rglob('*.kt'):
 if p.name in ['ScaleFactory.kt','ModernScaleAdapter.kt']:continue
 s=p.read_text(encoding='utf-8')
 while '@Composable' in s:
  a=s.index('@Composable');fn=s.index('fun ',a);nl=s.find('\n',fn)
  if p.name=='ScaleCommunicator.kt':end=nl
  else:end=block(s,fn)
  s=s[:a]+s[end:]
 s=re.sub(r'^import androidx.compose.*\n','',s,flags=re.M)
 s=re.sub(r'^import com.health.openscale.core.facade.*\n','',s,flags=re.M)
 s=re.sub(r'    (settingsFacade: SettingsFacade,\n    measurementFacade: MeasurementFacade,\n    userFacade: UserFacade,)','    data: DriverData,',s)
 s=s.replace('context, settingsFacade, measurementFacade, userFacade, handler','context, data, handler')
 if p.name=='ScaleDeviceHandler.kt':
  a=s.index('enum class DeviceCapability(');b=block(s,a)
  s=s[:a]+'enum class DeviceCapability { BODY_COMPOSITION, TIME_SYNC, USER_SYNC, HISTORY_READ, LIVE_WEIGHT_STREAM, UNIT_CONFIG, BATTERY_LEVEL }'+s[b:]
 write(p.relative_to(src/'java/com/health/openscale'),s)
enums=(src/'java/com/health/openscale/core/data/Enums.kt').read_text(encoding='utf-8')
s=license+'package com.health.openscale.core.data\n'
for name in ['ActivityLevel','WeightUnit','MeasureUnit']:
 a=enums.index('enum class '+name);s+=enums[a:block(enums,a)]+'\n'
s+='enum class GenderType { MALE, FEMALE }\n'+enums[enums.index('enum class UnitType'):enums.index('enum class InputFieldType')]
alltext='\n'.join(p.read_text(encoding='utf-8') for p in base.rglob('*.kt'))
icons=sorted(set(re.findall(r'MeasurementTypeIcon\.(\w+)',alltext))|{'IC_DEFAULT'})
s+='enum class MeasurementTypeIcon { '+', '.join(icons)+' }\n'
write('core/data/DriverUnits.kt',s)
mt=(src/'java/com/health/openscale/core/data/MeasurementType.kt').read_text(encoding='utf-8')
s=license+'''package com.health.openscale.core.data
import java.util.Date
/** Headless typed metric identities; protocol values and units are unchanged. */
class MeasurementType {
 data class Key<T:Any>(val identity:String)
 companion object {
'''
for name,typ in re.findall(r'val ([A-Z_0-9]+): Key<([^>]+)>',mt):s+=f'  val {name}=Key<{typ}>("builtin.{name.lower()}")\n'
for fun,typ in [('devicePercent','Percent'),('deviceKg','Kg'),('deviceFloat','Float'),('deviceInt','Int'),('deviceText','String')]:
 s+=f'  fun {fun}(path:String,nameResId:Int,icon:MeasurementTypeIcon=MeasurementTypeIcon.IC_DEFAULT,color:Int=0,enabled:Boolean=true,pinned:Boolean=false,rightAxis:Boolean=false,isInternal:Boolean=false,wireUnit:UnitType=UnitType.NONE):Key<{typ}> = Key("ble."+path)\n'
s+=' }\n}\n';write('core/data/MeasurementType.kt',s)
write('core/utils/ConverterUtils.kt',(src/'java/com/health/openscale/core/utils/ConverterUtils.kt').read_text(encoding='utf-8'))
write('core/utils/LogManager.kt','''package com.health.openscale.core.utils
/** Deliberately suppress protocol payloads and personal measurements in production logs. */
object LogManager {
 fun d(tag:String,message:String){}; fun i(tag:String,message:String){}
 fun w(tag:String,message:String,t:Throwable?=null){};fun e(tag:String,message:String,t:Throwable?=null){}
}
''')
scanner=(src/'java/com/health/openscale/core/service/BleScanner.kt').read_text(encoding='utf-8')
a=scanner.index('data class ScannedDeviceInfo(');b=scanner.index('\n)',a)+2
write('core/service/ScannedDeviceInfo.kt',license+'package com.health.openscale.core.service\nimport java.util.UUID\nimport android.util.SparseArray\n'+scanner[a:b])
modern=(src/'java/com/health/openscale/core/bluetooth/scales/ModernScaleAdapter.kt').read_text(encoding='utf-8')
a=modern.index('data class CommonTuning');b=modern.index('// -------------------------------------------------------------------------------------------------',a)
tuning=modern[a:b];a=tuning.index('enum class TuningProfile(');b=block(tuning,a);tuning=tuning[:a]+'enum class TuningProfile { Conservative, Balanced, Aggressive }\n'+tuning[b:]
write('core/bluetooth/scales/Tuning.kt',license+'package com.health.openscale.core.bluetooth.scales\nimport android.bluetooth.le.ScanSettings\n'+tuning)
factory=(src/'java/com/health/openscale/core/bluetooth/ScaleFactory.kt').read_text(encoding='utf-8')
a=factory.index('listOf(',factory.index('internal fun createHandlers'));b=factory.index('\n        )',a)+10
write('core/bluetooth/DriverCatalog.kt',license+'package com.health.openscale.core.bluetooth\nimport com.health.openscale.core.bluetooth.scales.*\nobject DriverCatalog { fun createHandlers():List<ScaleDeviceHandler> = '+factory[a:b]+'\n}\n')
# Only resources actually used by the driver sources, keeping original translations.
refs=set(re.findall(r'R\.string\.(\w+)', '\n'.join(p.read_text(encoding='utf-8') for p in base.rglob('*.kt'))))
for lang in ['values','values-de']:
 root=ET.Element('resources')
 for file in (src/'res'/lang).glob('*.xml'):
  for item in ET.parse(file).getroot():
   if item.tag=='string' and item.get('name') in refs:root.append(item)
 dest=out/'res'/lang;dest.mkdir(parents=True,exist_ok=True)
 ET.ElementTree(root).write(dest/'driver_strings.xml',encoding='utf-8',xml_declaration=True)
(out/'AndroidManifest.xml').write_text('<manifest xmlns:android="http://schemas.android.com/apk/res/android" />')
print('Imported',len(list(base.rglob('*.kt'))),'Kotlin files and',len(refs),'resource keys')
