/*
 * openScale
 * Copyright (C) 2025 olie.xdev <olie.xdeveloper@googlemail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.health.openscale.core.data
import java.util.Date
/** Headless typed metric identities; protocol values and units are unchanged. */
class MeasurementType {
 data class Key<T:Any>(val identity:String)
 companion object {
  val WEIGHT=Key<Kg>("builtin.weight")
  val BMI=Key<Float>("builtin.bmi")
  val BODY_FAT=Key<Percent>("builtin.body_fat")
  val WATER=Key<Percent>("builtin.water")
  val MUSCLE=Key<Percent>("builtin.muscle")
  val LBM=Key<Kg>("builtin.lbm")
  val BONE=Key<Kg>("builtin.bone")
  val WAIST=Key<Cm>("builtin.waist")
  val WHR=Key<Float>("builtin.whr")
  val WHTR=Key<Float>("builtin.whtr")
  val HIPS=Key<Cm>("builtin.hips")
  val VISCERAL_FAT=Key<Float>("builtin.visceral_fat")
  val CHEST=Key<Cm>("builtin.chest")
  val THIGH=Key<Cm>("builtin.thigh")
  val BICEPS=Key<Cm>("builtin.biceps")
  val NECK=Key<Cm>("builtin.neck")
  val CALIPER_1=Key<Cm>("builtin.caliper_1")
  val CALIPER_2=Key<Cm>("builtin.caliper_2")
  val CALIPER_3=Key<Cm>("builtin.caliper_3")
  val CALIPER=Key<Percent>("builtin.caliper")
  val BMR=Key<Kcal>("builtin.bmr")
  val TDEE=Key<Kcal>("builtin.tdee")
  val HEART_RATE=Key<Bpm>("builtin.heart_rate")
  val IMPEDANCE=Key<Ohm>("builtin.impedance")
  val IMPEDANCE_LOW=Key<Ohm>("builtin.impedance_low")
  val PROTEIN=Key<Percent>("builtin.protein")
  val CALORIES=Key<Kcal>("builtin.calories")
  val COMMENT=Key<String>("builtin.comment")
  val DATE=Key<java.util.Date>("builtin.date")
  val TIME=Key<java.util.Date>("builtin.time")
  val USER=Key<Unit>("builtin.user")
  fun devicePercent(path:String,nameResId:Int,icon:MeasurementTypeIcon=MeasurementTypeIcon.IC_DEFAULT,color:Int=0,enabled:Boolean=true,pinned:Boolean=false,rightAxis:Boolean=false,isInternal:Boolean=false,wireUnit:UnitType=UnitType.NONE):Key<Percent> = Key("ble."+path)
  fun deviceKg(path:String,nameResId:Int,icon:MeasurementTypeIcon=MeasurementTypeIcon.IC_DEFAULT,color:Int=0,enabled:Boolean=true,pinned:Boolean=false,rightAxis:Boolean=false,isInternal:Boolean=false,wireUnit:UnitType=UnitType.NONE):Key<Kg> = Key("ble."+path)
  fun deviceFloat(path:String,nameResId:Int,icon:MeasurementTypeIcon=MeasurementTypeIcon.IC_DEFAULT,color:Int=0,enabled:Boolean=true,pinned:Boolean=false,rightAxis:Boolean=false,isInternal:Boolean=false,wireUnit:UnitType=UnitType.NONE):Key<Float> = Key("ble."+path)
  fun deviceInt(path:String,nameResId:Int,icon:MeasurementTypeIcon=MeasurementTypeIcon.IC_DEFAULT,color:Int=0,enabled:Boolean=true,pinned:Boolean=false,rightAxis:Boolean=false,isInternal:Boolean=false,wireUnit:UnitType=UnitType.NONE):Key<Int> = Key("ble."+path)
  fun deviceText(path:String,nameResId:Int,icon:MeasurementTypeIcon=MeasurementTypeIcon.IC_DEFAULT,color:Int=0,enabled:Boolean=true,pinned:Boolean=false,rightAxis:Boolean=false,isInternal:Boolean=false,wireUnit:UnitType=UnitType.NONE):Key<String> = Key("ble."+path)
 }
}
