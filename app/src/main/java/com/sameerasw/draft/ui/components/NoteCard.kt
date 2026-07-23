package com.sameerasw.draft.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sameerasw.draft.R
import com.sameerasw.draft.data.model.Note
import com.sameerasw.draft.utils.ColorUtil
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun NoteCard(
    note: Note,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val title = note.title.ifBlank { "Untitled Note" }
    val dateString = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(Date(note.updatedAt * 1000))
    val bodySnippet = note.body.ifBlank { "No content" }

    ListItem(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }.clip(MaterialTheme.shapes.extraSmall).background(MaterialTheme.colorScheme.surfaceBright),
//        leadingContent = {
//            Box(
//                modifier = Modifier
//                    .size(40.dp)
//                    .clip(CircleShape),
//                contentAlignment = Alignment.Center
//            ) {
//                Icon(
//                    painter = painterResource(id = R.drawable.rounded_home_24),
//                    contentDescription = title,
//                    modifier = Modifier.size(24.dp),
//                    tint = ColorUtil.getVibrantColorFor(title)
//                )
//            }
//        },
        supportingContent = {
            Text(
                text = "$bodySnippet • $dateString",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        trailingContent = {
            if (note.isUnsynced) {
                Icon(
                    painter = painterResource(id = R.drawable.rounded_cloud_off_24),
                    contentDescription = "Not synced",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        },
        headlineContent = {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },

    )
}
