# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-keepclassmembers @kotlinx.serialization.Serializable class com.buyorbye.app.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
