package com.sameerasw.draft.ui.components.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sameerasw.draft.ui.components.containers.RoundedCardContainer
import com.sameerasw.draft.utils.HapticUtil

data class ChoiceOption<T>(
    val value: T,
    val title: String,
    val subtitle: String? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> SingleChoiceDialog(
    title: String,
    options: List<ChoiceOption<T>>,
    selectedValue: T,
    onSelect: (T) -> Unit,
    onDismissRequest: () -> Unit
) {
    val view = LocalView.current

    BasicAlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = Modifier.fillMaxWidth(0.92f)
    ) {
        RoundedCardContainer(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 24.dp,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                RoundedCardContainer(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 16.dp,
                    spacing = 2.dp,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                ) {
                    options.forEach { option ->
                        val isSelected = option.value == selectedValue
                        ListItem(
                            headlineContent = {
                                Text(
                                    text = option.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                )
                            },
                            supportingContent = if (option.subtitle != null) {
                                { Text(text = option.subtitle, style = MaterialTheme.typography.labelSmall) }
                            } else null,
                            trailingContent = {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = null
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.extraSmall)
                                .clickable {
                                    HapticUtil.performUIHaptic(view)
                                    onSelect(option.value)
                                    onDismissRequest()
                                },
                            colors = ListItemDefaults.colors(
                                containerColor = MaterialTheme.colorScheme.surfaceBright
                            )
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismissRequest) {
                        Text("Cancel")
                    }
                }
            }
        }
    }
}
