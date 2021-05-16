/** 生成的 Java 类名 */
@file:JvmName("Weaver")
package com.shijingfeng.module_event_dispatcher.auto_gradle_plugin.util

import com.shijingfeng.module_event_dispatcher.auto_gradle_plugin.extension.directoryNameToPackageName
import javassist.ClassPool
import java.io.File
import java.io.FileOutputStream
import java.util.jar.JarFile
import java.util.jar.JarOutputStream
import java.util.zip.ZipEntry
import org.apache.commons.io.IOUtils
import org.objectweb.asm.*
import java.io.InputStream

/**
 * Function: 代码织入器
 * Date: 2020/12/13 17:03
 * Description:
 * Author: ShiJingFeng
 */
internal class Weaver(
    printUtil: PrintUtil,
    classNameSet: MutableSet<String>
) {

    /** Build output 控制台输出工具类 */
    private val mPrintUtil = printUtil
    /** 注解执行器自动生成的类 全限定名称(. 被替换成了 /) Set (因为多渠道打包的原因, 故使用Set而不用List) */
    private val mClassNameSet = classNameSet

    /**
     * 织入代码
     *
     * @param src 资源文件 (当前插件的 input 文件, 上一个插件的 output 文件)
     * @return 临时的修改后的文件
     */
    fun weave(
        src: File
    ): File {
        if (mClassNameSet.isNotEmpty() && src.exists()) {
            val srcJarFile = JarFile(src)
            val srcEnumeration = srcJarFile.entries()
            val destTempFile = File(src.parent, src.name + ".temp")

            destTempFile.deleteOnExit()
            destTempFile.createNewFile()

            JarOutputStream(FileOutputStream(destTempFile)).use { destTempOutputStream ->
                while (srcEnumeration.hasMoreElements()) {
                    val srcJarEntry = srcEnumeration.nextElement()
                    val srcEntryName = srcJarEntry.name
                    val srcZipEntry = ZipEntry(srcEntryName)

                    destTempOutputStream.putNextEntry(srcZipEntry)
                    srcJarFile.getInputStream(srcJarEntry).use { srcInputStream ->
                        if (com.shijingfeng.module_event_dispatcher.auto_gradle_plugin.constant.WEAVE_CLASS_FILE_PATH == srcEntryName) {
//                            val byteArray = weaveByJavassist(src)
                            val byteArray = weaveByASM(srcInputStream)

                            destTempOutputStream.write(byteArray)
                        } else {
                            destTempOutputStream.write(IOUtils.toByteArray(srcInputStream))
                        }
                    }
                    destTempOutputStream.closeEntry()
                }
            }
            srcJarFile.close()
            return destTempFile
        }
        return src
    }

    /**
     * 通过 Javassist 织入代码
     * 在 Gradle Transform 中使用 Javassit 会导致 Clear Build失败(被织入的类所属的模块(库模块代码会被打包成jar包)或jar包被javassist持有引用)
     *
     * @param src 资源文件 (当前插件的 input 文件, 上一个插件的 output 文件)
     * @return 临时的修改后的文件
     */
    private fun weaveByJavassist(
        src: File
    ): ByteArray {
        // 导入jar包中类加载需要的路径
        val pool = ClassPool.getDefault()
        val jarClassPath = pool.insertClassPath(src.absolutePath)
        val weavedCtClass = pool.get(com.shijingfeng.module_event_dispatcher.auto_gradle_plugin.constant.WEAVE_CLASS_QUALIFIED_NAME)
        val weavedCtMethod = weavedCtClass.getDeclaredMethod(com.shijingfeng.module_event_dispatcher.auto_gradle_plugin.constant.WEAVE_METHOD_NAME)

        mClassNameSet.forEach { className ->
            val newClassName = className.directoryNameToPackageName()

            weavedCtMethod.insertAfter("{ ${com.shijingfeng.module_event_dispatcher.auto_gradle_plugin.constant.LOAD_METHOD_NAME}(\"$newClassName\"); }")
        }

        val byteArray = weavedCtClass.toBytecode()

        weavedCtClass.detach()
        pool.removeClassPath(jarClassPath)
        return byteArray
    }

    /**
     * 通过 ASM 织入代码
     *
     * @param inputStream 资源文件输入流 (当前插件的 input 文件, 上一个插件的 output 文件)
     * @return 临时的修改后的文件
     */
    private fun weaveByASM(
        inputStream: InputStream
    ): ByteArray {
        val cr = ClassReader(inputStream)
        // flags: 0 表示 ASM 不会自动自动帮你计算栈帧和局部变量表和操作数栈大小
        val cw = ClassWriter(cr, 0)
        val cv = WeaveClassVisitor(
            api = Opcodes.ASM7,
            cv = cw,
            printUtil = mPrintUtil,
            classNameSet = mClassNameSet
        )

        // ClassReader.EXPAND_FRAMES 用于设置扩展栈帧图。默认栈图以它们原始格式（V1_6以下使用扩展格式，其他使用压缩格式）被访问。
        // 如果设置该标识，栈图则始终以扩展格式进行访问（此标识在ClassReader和ClassWriter中增加了解压/压缩步骤，会大幅度降低性能）。
        cr.accept(cv, ClassReader.EXPAND_FRAMES)

        return cw.toByteArray()
    }

}

/**
 * 代码织入 ClassVisitor
 */
private class WeaveClassVisitor(
    api: Int,
    cv: ClassVisitor,
    printUtil: PrintUtil,
    classNameSet: MutableSet<String>
) : ClassVisitor(api, cv) {

    /** ASM版本 例如: [Opcodes.ASM5] */
    private val mApi = api
    /** Build Output 控制台输出工具类 */
    private val mPrintUtil = printUtil
    /** 注解执行器自动生成的类 全限定名称(. 被替换成了 /) Set (因为多渠道打包的原因, 故使用Set而不用List) */
    private val mClassNameSet = classNameSet

    override fun visitMethod(
        access: Int,
        name: String?,
        descriptor: String?,
        signature: String?,
        exceptions: Array<out String>?
    ): MethodVisitor {
        var methodVisitor = super.visitMethod(access, name, descriptor, signature, exceptions)

        if (com.shijingfeng.module_event_dispatcher.auto_gradle_plugin.constant.WEAVE_METHOD_NAME == name) {
            // 当前方法名为 WEAVE_METHOD_NAME
            methodVisitor = WeaveMethodVisitor(
                api = mApi,
                mv = methodVisitor,
                printUtil = mPrintUtil,
                classNameSet = mClassNameSet,
            )
        }
        return methodVisitor
    }

}

/**
 * 代码织入 MethodVisitor
 */
private class WeaveMethodVisitor(
    api: Int,
    mv: MethodVisitor,
    printUtil: PrintUtil,
    classNameSet: MutableSet<String>
) : MethodVisitor(api, mv) {

    /** Build Output 控制台输出工具类 */
    private val mPrintUtil = printUtil
    /** 注解执行器自动生成的类 全限定名称(. 被替换成了 /) Set (因为多渠道打包的原因, 故使用Set而不用List) */
    private val mClassNameSet = classNameSet

    /**
     * Visits a zero operand instruction.
     *
     * @param opcode the opcode of the instruction to be visited. This opcode is either NOP,
     * ACONST_NULL, ICONST_M1, ICONST_0, ICONST_1, ICONST_2, ICONST_3, ICONST_4, ICONST_5,
     * LCONST_0, LCONST_1, FCONST_0, FCONST_1, FCONST_2, DCONST_0, DCONST_1, IALOAD, LALOAD,
     * FALOAD, DALOAD, AALOAD, BALOAD, CALOAD, SALOAD, IASTORE, LASTORE, FASTORE, DASTORE,
     * AASTORE, BASTORE, CASTORE, SASTORE, POP, POP2, DUP, DUP_X1, DUP_X2, DUP2, DUP2_X1, DUP2_X2,
     * SWAP, IADD, LADD, FADD, DADD, ISUB, LSUB, FSUB, DSUB, IMUL, LMUL, FMUL, DMUL, IDIV, LDIV,
     * FDIV, DDIV, IREM, LREM, FREM, DREM, INEG, LNEG, FNEG, DNEG, ISHL, LSHL, ISHR, LSHR, IUSHR,
     * LUSHR, IAND, LAND, IOR, LOR, IXOR, LXOR, I2L, I2F, I2D, L2I, L2F, L2D, F2I, F2L, F2D, D2I,
     * D2L, D2F, I2B, I2C, I2S, LCMP, FCMPL, FCMPG, DCMPL, DCMPG, IRETURN, LRETURN, FRETURN,
     * DRETURN, ARETURN, RETURN, ARRAYLENGTH, ATHROW, MONITORENTER, or MONITOREXIT.
     */
    override fun visitInsn(opcode: Int) {
        // 在 return 之前织入代码 (return相关opcode码值范围 [Opcodes.IRETURN, Opcodes.RETURN])
        if (opcode in Opcodes.IRETURN..Opcodes.RETURN) {
            mClassNameSet.forEach { className ->
                val newClassName = className.directoryNameToPackageName()

                // 调用实例方法传的参数 举例: "com.shijingfeng.auto_generate.ApplicationModuleDataLoader$$app"
                mv.visitLdcInsn(newClassName)
                // 调用实例方法 举例: load("com.shijingfeng.auto_generate.ApplicationModuleDataLoader$$app")
                mv.visitMethodInsn(
                    Opcodes.INVOKESTATIC,
                    com.shijingfeng.module_event_dispatcher.auto_gradle_plugin.constant.WEAVE_CLASS_FILE_PATH_NO_EXTENSION,
                    com.shijingfeng.module_event_dispatcher.auto_gradle_plugin.constant.LOAD_METHOD_NAME,
                    com.shijingfeng.module_event_dispatcher.auto_gradle_plugin.constant.LOAD_METHOD_SIGN,
                    false
                )
            }
        }
        super.visitInsn(opcode)
    }

    override fun visitMaxs(maxStack: Int, maxLocals: Int) {
        super.visitMaxs(maxStack + 1, maxLocals)
    }
}
