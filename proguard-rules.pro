# Keep serialization metadata for text blocks
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class com.example.inknotes.model.** { *** Companion; }
-keep,includedescriptorclasses class com.example.inknotes.model.**$$serializer { *; }
