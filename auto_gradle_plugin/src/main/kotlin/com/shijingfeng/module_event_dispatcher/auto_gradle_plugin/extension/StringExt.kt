/** 生成的 Java 类名 */
@file:JvmName("StringExt")
package com.shijingfeng.module_event_dispatcher.auto_gradle_plugin.extension

import java.io.File

/**
 * Function: String 相关扩展函数
 * Date: 2020/12/14 12:56
 * Description:
 * Author: ShiJingFeng
 */

/** 目录名 转成 包名 */
internal fun String.directoryNameToPackageName() = this.replace("/", ".")

/** 包名 转成 目录名 */
internal fun String.packageNameToDirectoryName() = this.replace(".", "/")

/**
 * 替换为标准文件分隔符(Unix系统(包括Linux)和Mac系统所使用的文件分隔符 /)
 */
internal fun String.replaceToStandardFileSeparator(): String {
    if ("/" != File.separator) {
        return replace(File.separator, "/")
    }
    return this
}