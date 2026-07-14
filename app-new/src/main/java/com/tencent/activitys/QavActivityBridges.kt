package com.tencent.activitys

import rj.qmce.lite.ui.call.QmceCallActivity

/**
 * QAV 控制层的历史入口名。
 *
 * 两个入口都落到 QMCE 自有 Compose 通话页，不加载官方通话 Activity/UI。
 */
class BeInvitedActivity : QmceCallActivity()

class QQNTC2CWatchActivity : QmceCallActivity()
