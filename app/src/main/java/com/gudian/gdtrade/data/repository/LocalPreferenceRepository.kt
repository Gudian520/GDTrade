package com.gudian.gdtrade.data.repository

import android.content.Context

/**
 * 保留原类名以兼容现有 ViewModel 装配；实际持久化已经由 Room 完成。
 * SharedPreferences 仅用于首次升级时导入旧数据。
 */
class LocalPreferenceRepository(context: Context) : RoomTradeRepository(context)
