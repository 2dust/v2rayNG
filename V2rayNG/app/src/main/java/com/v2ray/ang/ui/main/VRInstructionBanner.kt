package com.v2ray.ang.ui.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun VRInstructionBanner(
    onDismiss: () -> Unit = {}
) {
    var isVisible by remember { mutableStateOf(true) }

    AnimatedVisibility(visible = isVisible) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(16.dp)),
            color = Color(0xCC121212),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "🚀 Быстрый старт OneTap VR",
                        color = Color(0xFF00FF88),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(
                        onClick = {
                            isVisible = false
                            onDismiss()
                        },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Закрыть",
                            tint = Color.Gray
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                InstructionStep(
                    number = "1", 
                    text = "Зайдите в Telegram-бот @one_tap_vpn_bot и получите QR-код подписки."
                )
                InstructionStep(
                    number = "2", 
                    text = "В этом приложении на шлеме нажмите «+» в верхнем углу экрана."
                )
                InstructionStep(
                    number = "3", 
                    text = "Выберите «Импорт из QR-кода» и наведите камеру очков на QR-код."
                )
                InstructionStep(
                    number = "4", 
                    text = "Выберите появившийся сервер и нажмите фиолетовую кнопку подключения!"
                )
            }
        }
    }
}

@Composable
private fun InstructionStep(number: String, text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .padding(top = 2.dp)
                .size(20.dp)
                .background(Color(0xFF8A2BE2), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = number,
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = text,
            color = Color.White,
            fontSize = 13.sp,
            lineHeight = 17.sp
        )
    }
}
