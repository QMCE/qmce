# QMCE (LX)

## What's this?

QMCE 是基于表 Q 内核及部分包装代码的一个 QQ 手表实现，主要使用 Kotlin 开发， UI 使用 Wear Compose 完成。
UI 主要适配圆表，方表没做~~因为太懒~~

目前项目还在开发中，别急 ~~好开发都被隔壁挖走了能快就见鬼了~~

## Where to get?

加入 QQ 群 1082439478 ，入群看消息和公告

## Developments.

### Build?

很简单 先克隆然后跑`./gradlew assemble`即可

### Notes.
 - `moye.*` 现在是 stubs ，没有修改必要，懒得 patch 了
 - 空白接口类基本上是防止 IDEA 类 IDE 炸错误的，不影响运行
 - `rj.qmce.lite.fix` 下面基本上都是修复/反检测，能跑你就别动，动了会炸
 - 别删 proguard 规则， `qq-sdk.jar` `qav-runtime.jar` 里部分代码引用会引用 QMCE 代码里的东西，比如 `rj.qmce.lite.Flag`

## Join us.

欢迎正常的、会看代码（而不是纯 vibe ）的开发加入！ QMCE 正是~~缺 token 的时候~~需要人的时候
加群联系即可
