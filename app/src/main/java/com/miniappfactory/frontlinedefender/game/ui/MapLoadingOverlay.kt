package com.miniappfactory.frontlinedefender.game.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.miniappfactory.frontlinedefender.R
import com.miniappfactory.frontlinedefender.ui.theme.SleekBorderLight
import com.miniappfactory.frontlinedefender.ui.theme.SleekGold
import com.miniappfactory.frontlinedefender.ui.theme.SleekRedText
import com.miniappfactory.frontlinedefender.ui.theme.SleekSurfaceCard
import com.miniappfactory.frontlinedefender.ui.theme.SleekTextAccent

/**
 * HARITA ARKAPLANI GOSTERGESI (FD-018/019).
 *
 * Soguk savasa giriste harita arkaplani (boyali pad yuvalari + yol) decode+
 * recolor ile gelene kadar gorunur; bu sirada motor PREPARATION'i DONDURUR ve
 * saha dokunusu/erken dalga YOK (`GameEngine.setMapReady(false)`).
 *
 * - [failed] = false: "yukleniyor" + donen gosterge.
 * - [failed] = true (FD-019): yukleme AYNI hedefte retry'lara ragmen basarisiz.
 *   Oyun ASKIDA kalmaz ama KILITLI kalir — yanlis harita ASLA gosterilmez;
 *   oyuncuya [onRetry] (ayni haritayi yeniden dene) ve [onLevelSelect] (haritaya
 *   don) sunulur.
 */
@Composable
fun MapLoadingOverlay(
    failed: Boolean = false,
    onRetry: () -> Unit = {},
    onLevelSelect: () -> Unit = {},
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        if (!failed) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .background(SleekSurfaceCard.copy(alpha = 0.88f), RoundedCornerShape(12.dp))
                    .border(1.dp, SleekBorderLight, RoundedCornerShape(12.dp))
                    .padding(horizontal = 18.dp, vertical = 12.dp)
                    .testTag("map_loading_overlay")
            ) {
                CircularProgressIndicator(
                    strokeWidth = 2.5.dp,
                    color = SleekGold,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = stringResource(R.string.map_loading),
                    color = SleekTextAccent,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    maxLines = 1
                )
            }
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .background(SleekSurfaceCard.copy(alpha = 0.92f), RoundedCornerShape(12.dp))
                    .border(1.dp, SleekBorderLight, RoundedCornerShape(12.dp))
                    .padding(horizontal = 20.dp, vertical = 16.dp)
                    .testTag("map_failed_overlay")
            ) {
                Text(
                    text = stringResource(R.string.map_load_failed),
                    color = SleekRedText,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = onLevelSelect, modifier = Modifier.testTag("map_failed_level_select")) {
                        Text(stringResource(R.string.dialog_level_select), fontSize = 13.sp)
                    }
                    Button(onClick = onRetry, modifier = Modifier.testTag("map_failed_retry")) {
                        Text(stringResource(R.string.map_retry), fontSize = 13.sp)
                    }
                }
            }
        }
    }
}
