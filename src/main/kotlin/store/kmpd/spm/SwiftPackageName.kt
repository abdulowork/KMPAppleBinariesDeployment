package store.kmpd.spm

sealed class SwiftPackageName {
    data class Specific(val value: String) : SwiftPackageName()
    class FromProjectName : SwiftPackageName()
}