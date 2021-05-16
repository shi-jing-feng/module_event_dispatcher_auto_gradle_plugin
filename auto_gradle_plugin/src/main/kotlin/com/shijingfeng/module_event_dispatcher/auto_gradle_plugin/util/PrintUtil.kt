package com.shijingfeng.module_event_dispatcher.auto_gradle_plugin.util

import org.gradle.api.logging.LogLevel
import org.gradle.api.logging.Logger

/** 默认日志前缀 */
internal const val DEFAULT_LOG_PREFIX = "ModuleEventDispatcher->>: "

/**
 * Function: Gradle Build Output 打印日志 工具类
 * Date: 2020/12/4 15:34
 * Description:
 * Author: ShiJingFeng
 */
internal class PrintUtil private constructor(
    logger: Logger
) {

    /** Logger */
    private val mLogger = logger

    /** 是否开启日志输出  true:开启 */
    private var mEnable = true
    /** 日志前缀 */
    private var mLogPrefix = DEFAULT_LOG_PREFIX
    /** 日志级别 */
    private var mLogLevel = LogLevel.INFO

    companion object {

        /** 创建实例 */
        fun create(logger: Logger) = PrintUtil(logger)

    }

    /**
     * 是否开启日志  true:开启
     */
    var enable: Boolean
        get() = this.mEnable
        set(enable) { this.mEnable = enable }

    /**
     * 日志前缀
     */
    var logPrefix: String
        get() = this.mLogPrefix
        set(logPrefix) { this.mLogPrefix = logPrefix }

    /**
     * 日志级别
     */
    var logLevel: LogLevel
        get() = this.mLogLevel
        set(logLevel) { this.mLogLevel = logLevel }

    /**
     * 打印 警告信息
     */
    fun print(
        message: String = ""
    ) {
        if (!mEnable) {
            return
        }
        mLogger.log(mLogLevel, mLogPrefix + message)
    }

    override fun toString() = "是否开启日志:$mEnable  日志前缀:$mLogPrefix  日志级别:${mLogLevel.name}"

}