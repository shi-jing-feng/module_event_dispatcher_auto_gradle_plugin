/** 生成的 Java 类名 */
@file:JvmName("CustomPlugin")
package com.shijingfeng.module_event_dispatcher.auto_gradle_plugin.plugin

import com.android.build.gradle.AppExtension
import com.android.build.gradle.AppPlugin
import com.shijingfeng.module_event_dispatcher.auto_gradle_plugin.tranform.ModuleEventDispatcherTransform
import com.shijingfeng.module_event_dispatcher.auto_gradle_plugin.util.DEFAULT_LOG_PREFIX
import com.shijingfeng.module_event_dispatcher.auto_gradle_plugin.util.PrintUtil
import org.gradle.api.Action
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.logging.LogLevel

/**
 * Function: 自定义插件
 * Date: 2020/12/4 15:01
 * Description:
 * Author: ShiJingFeng
 */
internal class ModuleEventDispatcherPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        val isAppPlugin = project.plugins.hasPlugin(AppPlugin::class.java)

        if (!isAppPlugin) {
            throw GradleException("必须是Application插件")
        }
        // android {} 闭包扩展
        val appExtension = project.extensions.getByType(AppExtension::class.java)
        val printUtil = PrintUtil.create(project.logger)

        project.extensions.create("moduleEventDispatcher", ModuleEventDispatcherExt::class.java)
//        def customTransformForDebugTask = project.getTasks().findByPath(":app:transformClassesWithCom.shijingfeng.apt_gradle_plugin.transform.CustomTransformForDebug")
//
//          //此处 customTransformForDebugTask 会为 null, 应为该任务还没创建
//        customTransformForDebugTask.doFirst {
//            project.logger.log(LogLevel.DEBUG, "在插件前运行")
//            printUtil.enable(moduleEventDispatcherExtension.enableLog)
//        }
//        customTransformForDebugTask.doLast {
//            project.logger.log(LogLevel.DEBUG, "在插件后运行")
//        }

        appExtension.registerTransform(
            ModuleEventDispatcherTransform(
            project = project,
            printUtil = printUtil
        )
        )
    }
}

/**
 * 模块事件分发器 扩展
 * 注意: 类修饰符必须是open(在Java中即: 类不是final的, 是可继承的)
 */
internal open class ModuleEventDispatcherExt {

    /** 日志扩展 (注意不能为空, 否则 log(Action<ModuleEventDispatcherLogExt>) 会空指针异常) */
    private val mLog = ModuleEventDispatcherLogExt()

    /**
     * 内部 log 扩展
     */
    fun logConfig(action: Action<ModuleEventDispatcherLogExt>) {
        action.execute(mLog)
    }

    /**
     * 获取 日志扩展
     */
    val log
        get() = mLog

}

/**
 * 模块事件分发器 日志 扩展
 * 注意: 类修饰符必须是open(在Java中即: 类不是final的, 是可继承的)
 */
internal open class ModuleEventDispatcherLogExt {

    /** 是否开启日志  true:开启 */
    private var mEnable = false

    /** 日志前缀 默认:[DEFAULT_LOG_PREFIX] */
    private var mLogPrefix = DEFAULT_LOG_PREFIX

    /** 日志级别 默认:[LogLevel.INFO] */
    private var mLogLevel = LogLevel.INFO

    /** 获取 或 设置 是否开启日志  true:开启 */
    var enable
        get() = this.mEnable
        set(enable) { this.mEnable = enable }

    /** 获取 或 设置 日志前缀 默认:[DEFAULT_LOG_PREFIX] */
    var logPrefix
        get() = this.mLogPrefix
        set(logPrefix) { this.mLogPrefix = logPrefix }

    /** 获取 或 设置 日志级别 默认:[LogLevel.INFO] */
    var logLevel
        get() = this.mLogLevel
        set(logLevel) { this.mLogLevel = logLevel }

}