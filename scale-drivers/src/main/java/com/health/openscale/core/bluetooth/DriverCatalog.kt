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
package com.health.openscale.core.bluetooth
import com.health.openscale.core.bluetooth.scales.*
object DriverCatalog { fun createHandlers():List<ScaleDeviceHandler> = listOf(
            // Exact-name match must precede generic LeFu/0xFFF0 handlers (first match wins).
            HealthKeep280Handler(),
            PicoocHandler(),
            ActiveEraBF06Handler(),
            AfuB1Handler(),
            KeepS3Handler(),
            OmronWlcHandler(),
            BeurerBF450Handler(),
            TaylorBIAHandler(),
            RyFitHandler(),
            CultSmartScaleProHandler(),
            RealmeSmartScaleHandler(),
            YunmaiHandler(isMini = false),
            YunmaiHandler(isMini = true),
            YunmaiXHandler(),
            TrisaBodyAnalyzeHandler(),
            SanitasSbf72Handler(),
            StandardBeurerSanitasHandler(),
            SoehnleHandler(),
            SinocareHandler(),
            SenssunHandler(),
            RenphoHandler(),
            AiLinkBroadcastHandler(),
            QNHandlerBroadcast(),
            QNHandler(),
            OneByoneHandler(),
            OneByoneNewHandler(),
            OkOkHandler(),
            MiScaleS400Handler(),
            XiaomiS800Handler(),
            MiScaleHandler(),
            RunstarR6Handler(),
            RunstarR5Handler(),
            RelaxmedicHandler(),
            RobiS9Handler(),
            VitafitVT701Handler(),
            EEBBLHandler(),
            FitTrackDaraHandler(),
            DrTrustSSW532Handler(),
            MGBHandler(),
            MedisanaBs44xHandler(),
            InlifeHandler(),
            IHealthHS3Handler(),
            HuaweiAhCh100Handler(),
            HuaweiCH100SHandler(),
            HuaweiHagridWspHandler(),
            HoffenBbs8107Handler(),
            HesleyHandler(),
            ExingtechY1Handler(),
            EbelterBodyFatB2Handler(),
            HumeDara2Handler(),
            ExcelvanCF36xHandler(),
            EtekcityESF551Handler(),
            EtekcityFit8SHandler(),
            EufyC20Handler(),
            EufyP2Handler(),
            ESCS20MHandler(),
            RenphoES26BBHandler(),
            DigooDGSO38HHandler(),
            CustomOpenScaleHandler(),
            BeurerSanitasHandler(),
            AAAxHandler(),
            ScaleupHandler(),
            BodyConnectHandler(),
            WeightGurusA3Handler(),
        )
}
