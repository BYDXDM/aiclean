package com.example.aiclean.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.aiclean.core.cleaner.AccessLevel

@Composable
fun AccessLevelCard(accessLevel: AccessLevel, onRequestShizuku: () -> Unit, modifier: Modifier = Modifier) {
    val (title, description, icon, color) = when (accessLevel) {
        AccessLevel.ROOT -> AccessLevelInfo("Root 权限", "已启用完全系统权限", Icons.Default.Security, Color(0xFF4CAF50))
        AccessLevel.SHIZUKU -> AccessLevelInfo("Shizuku 权限", "已连接 Shizuku 高级权限", Icons.Default.CheckCircle, Color(0xFF2196F3))
        AccessLevel.NORMAL -> AccessLevelInfo("普通权限", "可连接 Shizuku 以清理应用缓存", Icons.Default.Lock, Color(0xFFFF9800))
    }
    Card(modifier = modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f))) {
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(color.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = color)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (accessLevel == AccessLevel.NORMAL) {
                Button(onClick = onRequestShizuku) { Text("连接") }
            }
        }
    }
}

private data class AccessLevelInfo(val title: String, val description: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val color: Color)
