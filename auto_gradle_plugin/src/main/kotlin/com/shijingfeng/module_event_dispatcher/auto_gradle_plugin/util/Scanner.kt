/** 生成的 Java 类名 */
@file:JvmName("Scanner")
package com.shijingfeng.module_event_dispatcher.auto_gradle_plugin.util

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import java.io.File
import java.io.InputStream
import java.util.jar.JarFile

/**
 * Function: 扫描器
 * Date: 2020/12/10 22:43
 * Description:
 * Author: ShiJingFeng
 */
internal class Scanner(
    printUtil: PrintUtil? = null,
    classNameSet: MutableSet<String>
) {

    /** Build output 控制台输出工具类 */
    private val mPrintUtil = printUtil
    /** 注解执行器自动生成的类 全限定名称(. 被替换成了 /) Set (因为多渠道打包的原因, 故使用Set而不用List) */
    private val mClassNameSet = classNameSet

    /**
     * 扫描 jar 包
     *
     * @param src 插件输入文件
     * @return 是否包含 要织入的类  true: 包含  false: 不包含
     */
    fun scanJar(src: File): Boolean {
        var include = false

        if (src.exists()) {
            JarFile(src).use { srcJarFile ->
                val enumeration = srcJarFile.entries()

                while (enumeration.hasMoreElements()) {
                    val jarEntry = enumeration.nextElement()
                    val entryName = jarEntry.name

                    if (entryName.startsWith(com.shijingfeng.module_event_dispatcher.auto_gradle_plugin.constant.AUTO_GENERATE_FILE_DIRECTORY)) {
                        scanClass(srcJarFile.getInputStream(jarEntry))
                    } else if (com.shijingfeng.module_event_dispatcher.auto_gradle_plugin.constant.WEAVE_CLASS_FILE_PATH == entryName && !include) {
                        include = true
                    }
                }
            }
        }
        return include
    }

    /**
     * 扫描类
     * 关于ASM具体请看: https://www.jianshu.com/p/abd1b1b8d3f3
     *
     * @param inputStream 文件输入流
     */
    fun scanClass(
        inputStream: InputStream
    ) {
        inputStream.use {
            val cr = ClassReader(inputStream)
            // flags: 0 表示 ASM 不会自动自动帮你计算栈帧和局部变量表和操作数栈大小
            val cw = ClassWriter(cr, 0)
            val cv = ScanClassVisitor(
                printUtil = mPrintUtil,
                classNameSet = mClassNameSet,
                api = Opcodes.ASM7,
                cv = cw
            )

            // ClassReader.EXPAND_FRAMES 用于设置扩展栈帧图。默认栈图以它们原始格式（V1_6以下使用扩展格式，其他使用压缩格式）被访问。
            // 如果设置该标识，栈图则始终以扩展格式进行访问（此标识在ClassReader和ClassWriter中增加了解压/压缩步骤，会大幅度降低性能）。
            cr.accept(cv, ClassReader.EXPAND_FRAMES)
        }
    }

    /**
     * 是否需要扫描
     *
     * @return true: 需要扫描
     */
    fun needScan(path: String) = path.isNotEmpty() && path.startsWith(com.shijingfeng.module_event_dispatcher.auto_gradle_plugin.constant.AUTO_GENERATE_FILE_DIRECTORY)

}

/**
 * 类扫描器
 */
private class ScanClassVisitor(
    printUtil: PrintUtil? = null,
    classNameSet: MutableSet<String>,
    api: Int,
    cv: ClassVisitor
) : ClassVisitor(api, cv) {

    /** Build output 控制台输出工具类 */
    private val mPrintUtil = printUtil
    /** 注解执行器自动生成的类 全限定名称(. 被替换成了 /) Set (因为多渠道打包的原因, 故使用Set而不用List) */
    private val mClassNameSet = classNameSet

    override fun visit(
        // 例子: 51
        version: Int,
        // 例子: 33
        access: Int,
        // 例子: com/shijingfeng/auto_generate/ApplicationModuleDataLoader$$app
        name: String?,
        // 例子: null
        signature: String?,
        // 例子: java/lang/Object
        superName: String?,
        // 例子: com/shijingfeng/apt_data/interfaces/IApplicationModuleDataLoader
        interfaces: Array<out String>?
    ) {
        super.visit(version, access, name, signature, superName, interfaces)
        if (interfaces.isNullOrEmpty() || name.isNullOrEmpty()) {
            return
        }
        mPrintUtil?.print("""
            ++++++++++ 发现目标类开始 ++++++++++
            * version    : $version
            * access     : $access
            * name       : $name
            * signature  : $signature
            * superName  : $superName
            * interfaces : ${interfaces.joinToString(", ")}
            ++++++++++ 发现目标类结束 ++++++++++
        """.trimIndent())
        interfaces.forEach { interfaceName ->
            if (com.shijingfeng.module_event_dispatcher.auto_gradle_plugin.constant.AUTO_GENERATE_CLASS_INTERFACE_NAME == interfaceName) {
                // 因为多渠道打包的原因, 故使用Set而不用List
                // 此处的name是不带扩展名的文件目录名, 而不是包名
                mClassNameSet.add(name)
            }
        }
    }
}