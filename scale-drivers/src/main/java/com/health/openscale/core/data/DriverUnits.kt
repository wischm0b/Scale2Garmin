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
enum class ActivityLevel {
    SEDENTARY, MILD, MODERATE, HEAVY, EXTREME;

    fun toInt(): Int {
        when (this) {
            SEDENTARY -> return 0
            MILD -> return 1
            MODERATE -> return 2
            HEAVY -> return 3
            EXTREME -> return 4
        }
    }

    companion object {
        @JvmStatic
        fun fromInt(unit: Int): ActivityLevel {
            when (unit) {
                0 -> return SEDENTARY
                1 -> return MILD
                2 -> return MODERATE
                3 -> return HEAVY
                4 -> return EXTREME
            }
            return SEDENTARY
        }
    }
}
enum class WeightUnit {
    KG, LB, ST;

    override fun toString(): String {
        when (this) {
            LB -> return "lb"
            ST -> return "st"
            KG -> return "kg"
        }

    }

    fun toInt(): Int {
        when (this) {
            LB -> return 1
            ST -> return 2
            KG -> return 0
        }
    }
}
enum class MeasureUnit {
    CM, INCH;

    override fun toString(): String {
        when (this) {
            CM -> return "cm"
            INCH -> return "in"
        }
    }

    fun toInt(): Int {
        when (this) {
            CM -> return 0
            INCH -> return 1
        }
    }
}
enum class GenderType { MALE, FEMALE; fun isMale() = this == MALE }
enum class UnitType(val displayName: String) {
    KG("kg"),
    LB("lb"),
    ST("st"),
    PERCENT("%"),
    CM("cm"),
    INCH("in"),
    KCAL("kcal"),
    BPM("bpm"),
    OHM("Ω"),
    NONE("");

    fun isWeightUnit(): Boolean {
        return this == KG || this == LB || this == ST
    }

    /**
     * The units a value in this unit can be converted into without changing what it means.
     * PERCENT is alone in its family on purpose: turning a percentage into a mass needs to
     * know what it is a percentage *of*.
     */
    fun convertibleUnits(): List<UnitType> = when {
        isWeightUnit() -> listOf(KG, LB, ST)
        this == CM || this == INCH -> listOf(CM, INCH)
        else -> listOf(this)
    }

    fun toWeightUnit(): WeightUnit {
        return when (this) {
            LB -> WeightUnit.LB
            ST -> WeightUnit.ST
            else -> WeightUnit.KG
        }
    }
}

/**
 * A raw scale value whose unit is part of its type. Every float-valued builtin
 * [MeasurementType.Key] binds one of these ([Kg], [Percent], [Cm], [Kcal], [Ohm]), so a
 * handler cannot set a value without stating the unit it is in — `m[WEIGHT] = Kg(72.5f)`
 * compiles, `m[WEIGHT] = 72.5f` and `m[WEIGHT] = Percent(20f)` do not. The BLE connector
 * unwraps centrally and converts to the unit the user configured.
 *
 * The companions carry the conversions a handler needs when its scale reports something
 * else (`Kg.from(native, user.scaleUnit)`, `Cm.fromInch(...)`), so the right way is also
 * the discoverable way. `toString()` prints just the number to keep log lines readable.
 */
sealed interface UnitValue {
    val value: Float
}

/** A mass in kilograms — the delivery unit for WEIGHT, LBM, BONE and `deviceKg` keys. */
@JvmInline
value class Kg(override val value: Float) : UnitValue {
    override fun toString(): String = value.toString()

    companion object {
        /** Converts from the unit the scale natively reports in (kg/lb/st). */
        fun from(value: Float, unit: WeightUnit): Kg =
            Kg(com.health.openscale.core.utils.ConverterUtils.toKilogram(value, unit))

        fun fromLb(lb: Float): Kg = from(lb, WeightUnit.LB)

        /** Chinese catty (市斤), used natively by some Asian scales. */
        fun fromJin(jin: Float): Kg = Kg(jin * 0.5f)
    }
}

/** A share of body weight in percent — BODY_FAT, WATER, MUSCLE, PROTEIN, `devicePercent`. */
@JvmInline
value class Percent(override val value: Float) : UnitValue {
    override fun toString(): String = value.toString()
}

/** A length in centimeters — the circumference and caliper keys. */
@JvmInline
value class Cm(override val value: Float) : UnitValue {
    override fun toString(): String = value.toString()

    companion object {
        fun fromInch(inch: Float): Cm = Cm(inch * 2.54f)
    }
}

/** An energy in kilocalories — BMR (and CALORIES). */
@JvmInline
value class Kcal(override val value: Float) : UnitValue {
    override fun toString(): String = value.toString()
}

/** An electrical resistance in ohms — the impedance keys. */
@JvmInline
value class Ohm(override val value: Float) : UnitValue {
    override fun toString(): String = value.toString()
}

/** A heart rate in whole beats per minute. */
@JvmInline
value class Bpm(val value: Int) {
    override fun toString(): String = value.toString()
}

enum class MeasurementTypeIcon { IC_BICEPS, IC_CHEST, IC_DEFAULT, IC_M_BUBBLE_CHART, IC_M_HIVE, IC_M_SCATTER_PLOT, IC_THIGH }
