package rj.qmce.lite.wear

import android.content.Context
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.DeviceParametersBuilders
import androidx.wear.protolayout.DimensionBuilders
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.material3.MaterialScope
import androidx.wear.protolayout.material3.button
import androidx.wear.protolayout.material3.materialScope
import androidx.wear.protolayout.material3.primaryLayout
import androidx.wear.protolayout.material3.text
import androidx.wear.protolayout.material3.textEdgeButton
import androidx.wear.protolayout.types.layoutString
import rj.qmce.lite.BuildConfig
import rj.qmce.lite.notify.QmceMessageNotifier
import rj.qmce.lite.ui.MainActivity

internal object QmceWatchlistTileLayouts {
    enum class TileState {
        LOGGED_OUT,
        DISABLED,
        EMPTY,
        DATA,
        ERROR,
    }

    data class RowData(
        val name: String,
        val abstract: String,
        val peerUid: String,
        val peerUin: Long,
        val chatType: Int,
    )

    fun buildRoot(
        context: Context,
        deviceParameters: DeviceParametersBuilders.DeviceParameters,
        state: TileState,
        rows: List<RowData>,
    ): LayoutElementBuilders.LayoutElement {
        return materialScope(context, deviceParameters) {
            when (state) {
                TileState.LOGGED_OUT -> statusLayout(
                    message = "请先登录 QMCE",
                    cta = "打开应用",
                    clickable = launchMain(context),
                )
                TileState.DISABLED -> statusLayout(
                    message = "消息 Tile 已关闭",
                    cta = "打开应用",
                    clickable = launchMain(context),
                )
                TileState.EMPTY -> statusLayout(
                    message = "尚未添加群聊",
                    cta = "选取群聊",
                    clickable = launchGroupPicker(context),
                )
                TileState.ERROR -> statusLayout(
                    message = "暂时无法加载",
                    cta = "打开应用",
                    clickable = launchMain(context),
                )
                TileState.DATA -> primaryLayout(
                    titleSlot = { text("关注群聊".layoutString) },
                    mainSlot = { columnOfRows(context, rows.take(3)) },
                    bottomSlot = {
                        textEdgeButton(
                            onClick = launchGroupPicker(context),
                            labelContent = { text("管理选取".layoutString) },
                        )
                    },
                )
            }
        }
    }

    private fun MaterialScope.statusLayout(
        message: String,
        cta: String,
        clickable: ModifiersBuilders.Clickable,
    ): LayoutElementBuilders.LayoutElement = primaryLayout(
        mainSlot = { text(message.layoutString) },
        bottomSlot = {
            textEdgeButton(
                onClick = clickable,
                labelContent = { text(cta.layoutString) },
            )
        },
    )

    private fun MaterialScope.columnOfRows(
        context: Context,
        rows: List<RowData>,
    ): LayoutElementBuilders.LayoutElement {
        val column = LayoutElementBuilders.Column.Builder()
            .setWidth(DimensionBuilders.ExpandedDimensionProp.Builder().build())
        rows.forEach { row ->
            val abstract = row.abstract.take(40)
            column.addContent(
                button(
                    onClick = openChatClickable(context, row),
                    width = DimensionBuilders.ExpandedDimensionProp.Builder().build(),
                    labelContent = { text(row.name.take(24).layoutString) },
                    secondaryLabelContent = {
                        text(abstract.ifBlank { " " }.layoutString)
                    },
                ),
            )
        }
        return column.build()
    }

    private fun launchMain(context: Context): ModifiersBuilders.Clickable =
        clickableActivity(context) {}

    private fun launchGroupPicker(context: Context): ModifiersBuilders.Clickable =
        clickableActivity(context) {
            addKeyToExtraMapping(
                QmceMessageNotifier.EXTRA_OPEN_TILE_GROUP_PICKER,
                ActionBuilders.AndroidBooleanExtra.Builder().setValue(true).build(),
            )
        }

    private fun openChatClickable(
        context: Context,
        row: RowData,
    ): ModifiersBuilders.Clickable =
        clickableActivity(context) {
            addKeyToExtraMapping(
                QmceMessageNotifier.EXTRA_OPEN_CHAT,
                ActionBuilders.AndroidBooleanExtra.Builder().setValue(true).build(),
            )
            addKeyToExtraMapping(
                QmceMessageNotifier.EXTRA_PEER_UID,
                ActionBuilders.AndroidStringExtra.Builder().setValue(row.peerUid).build(),
            )
            addKeyToExtraMapping(
                QmceMessageNotifier.EXTRA_PEER_UIN,
                ActionBuilders.AndroidLongExtra.Builder().setValue(row.peerUin).build(),
            )
            addKeyToExtraMapping(
                QmceMessageNotifier.EXTRA_CHAT_TYPE,
                ActionBuilders.AndroidIntExtra.Builder().setValue(row.chatType).build(),
            )
            addKeyToExtraMapping(
                QmceMessageNotifier.EXTRA_PEER_NICKNAME,
                ActionBuilders.AndroidStringExtra.Builder().setValue(row.name).build(),
            )
        }

    private fun clickableActivity(
        context: Context,
        configure: ActionBuilders.AndroidActivity.Builder.() -> Unit,
    ): ModifiersBuilders.Clickable {
        val activity = ActionBuilders.AndroidActivity.Builder()
            .setClassName(MainActivity::class.java.name)
            .setPackageName(BuildConfig.APPLICATION_ID)
            .apply(configure)
            .build()
        return ModifiersBuilders.Clickable.Builder()
            .setOnClick(
                ActionBuilders.LaunchAction.Builder()
                    .setAndroidActivity(activity)
                    .build(),
            )
            .build()
    }
}
