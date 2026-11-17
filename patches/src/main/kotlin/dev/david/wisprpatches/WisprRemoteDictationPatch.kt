package dev.david.wisprpatches

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility

private val WISPR_287 = Compatibility(
    name = "Wispr Flow",
    packageName = "com.wispr.flowapp",
    apkFileType = ApkFileType.XAPK,
    appIconColor = 0xB7A6FE,
    targets = listOf(AppTarget(version = "2.8.7"))
)

private const val SERVICE = "Lcom/wispr/flowapp/service/FlowService;"
private const val BRIDGE = "Ldev/david/wisprpatches/bridge/RemoteDictationBridge;"

private object FlowServiceOnCreate : Fingerprint(
    definingClass = SERVICE,
    name = "onCreate",
    returnType = "V",
    parameters = emptyList()
)

private object FlowServiceOnDestroy : Fingerprint(
    definingClass = SERVICE,
    name = "onDestroy",
    returnType = "V",
    parameters = emptyList()
)

/**
 * Adds a runtime broadcast receiver to Wispr's existing, non-exported FlowService.
 * Experimental: requires device testing and does not modify the transcription service.
 */
@Suppress("unused")
val wisprRemoteDictationPatch = bytecodePatch(
    name = "Wispr Remote Dictation (experimental)",
    description = "Permit Gboard to toggle Wispr Flow dictation via a local broadcast.",
    default = false
) {
    compatibleWith(WISPR_287)
    extendWith("extensions/extension.mpe")

    execute {
        // For Wispr 2.8.7: inject after the initial super.onCreate() call.
        FlowServiceOnCreate.method.addInstruction(
            2,
            "invoke-static/range {p0 .. p0}, $BRIDGE->install(Landroid/app/Service;)V"
        )
        FlowServiceOnDestroy.method.addInstruction(
            0,
            "invoke-static/range {p0 .. p0}, $BRIDGE->uninstall(Landroid/app/Service;)V"
        )
    }
}
