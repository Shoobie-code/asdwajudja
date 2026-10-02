package org.spongepowered.asm.mixin;
public @interface Mixin { Class<?>[] value() default {}; int priority() default 1000; }
