package com.mdreal.cinematica.scene

import com.mdreal.cinematica.timeline.ActorKey
import com.mdreal.cinematica.timeline.CameraKey
import com.mdreal.cinematica.timeline.Ease
import com.mdreal.cinematica.timeline.FadeKey
import com.mdreal.cinematica.timeline.FogKey
import com.mdreal.cinematica.timeline.Pose
import com.mdreal.cinematica.timeline.Rot
import com.mdreal.cinematica.timeline.Scene
import com.mdreal.cinematica.timeline.Vec
import com.mojang.serialization.Codec
import com.mojang.serialization.DataResult
import com.mojang.serialization.codecs.RecordCodecBuilder
import java.util.Optional

/** JSON shape of a scene. Optional fields are left out when they hold their default, so saved files stay short. */
object SceneCodecs {
    val VEC: Codec<Vec> = Codec.DOUBLE.listOf().comapFlatMap(
        { if (it.size == 3) DataResult.success(Vec(it[0], it[1], it[2])) else DataResult.error { "Expected [x, y, z], got $it" } },
        { listOf(it.x, it.y, it.z) }
    )

    val ROT: Codec<Rot> = Codec.FLOAT.listOf().comapFlatMap(
        { if (it.size == 3) DataResult.success(Rot(it[0], it[1], it[2])) else DataResult.error { "Expected [x, y, z] degrees, got $it" } },
        { listOf(it.x, it.y, it.z) }
    )

    val EASE: Codec<Ease> = Codec.STRING.comapFlatMap(
        { name -> Ease.byName(name)?.let { DataResult.success(it) } ?: DataResult.error { "Unknown ease '$name'" } },
        Ease::serialName
    )

    val POSE: Codec<Pose> = RecordCodecBuilder.create { i ->
        i.group(
            ROT.optionalFieldOf("head", Rot.ZERO).forGetter(Pose::head),
            ROT.optionalFieldOf("body", Rot.ZERO).forGetter(Pose::body),
            ROT.optionalFieldOf("right_arm", Rot.ZERO).forGetter(Pose::rightArm),
            ROT.optionalFieldOf("left_arm", Rot.ZERO).forGetter(Pose::leftArm),
            ROT.optionalFieldOf("right_leg", Rot.ZERO).forGetter(Pose::rightLeg),
            ROT.optionalFieldOf("left_leg", Rot.ZERO).forGetter(Pose::leftLeg),
            ROT.optionalFieldOf("right_elbow", Rot.ZERO).forGetter(Pose::rightElbow),
            ROT.optionalFieldOf("left_elbow", Rot.ZERO).forGetter(Pose::leftElbow),
            ROT.optionalFieldOf("right_knee", Rot.ZERO).forGetter(Pose::rightKnee),
            ROT.optionalFieldOf("left_knee", Rot.ZERO).forGetter(Pose::leftKnee),
        ).apply(i, ::Pose)
    }

    val CAMERA_KEY: Codec<CameraKey> = RecordCodecBuilder.create { i ->
        i.group(
            Codec.INT.fieldOf("t").forGetter(CameraKey::t),
            VEC.fieldOf("pos").forGetter(CameraKey::pos),
            Codec.FLOAT.fieldOf("yaw").forGetter(CameraKey::yaw),
            Codec.FLOAT.fieldOf("pitch").forGetter(CameraKey::pitch),
            Codec.FLOAT.optionalFieldOf("roll", 0f).forGetter(CameraKey::roll),
            Codec.FLOAT.optionalFieldOf("fov").forGetter { Optional.ofNullable(it.fov) },
            EASE.optionalFieldOf("ease", Ease.LINEAR).forGetter(CameraKey::ease),
            Codec.floatRange(0f, 1f).optionalFieldOf("ground", 0f).forGetter(CameraKey::ground),
        ).apply(i) { t, pos, yaw, pitch, roll, fov, ease, ground -> CameraKey(t, pos, yaw, pitch, roll, fov.orElse(null), ease, ground) }
    }

    val ACTOR_KEY: Codec<ActorKey> = RecordCodecBuilder.create { i ->
        i.group(
            Codec.INT.fieldOf("t").forGetter(ActorKey::t),
            VEC.fieldOf("pos").forGetter(ActorKey::pos),
            Codec.FLOAT.fieldOf("yaw").forGetter(ActorKey::yaw),
            POSE.optionalFieldOf("pose", Pose.STANDING).forGetter(ActorKey::pose),
            ROT.optionalFieldOf("root", Rot.ZERO).forGetter(ActorKey::root),
            EASE.optionalFieldOf("ease", Ease.LINEAR).forGetter(ActorKey::ease),
        ).apply(i, ::ActorKey)
    }

    val FADE_KEY: Codec<FadeKey> = RecordCodecBuilder.create { i ->
        i.group(
            Codec.INT.fieldOf("t").forGetter(FadeKey::t),
            Codec.floatRange(0f, 1f).fieldOf("alpha").forGetter(FadeKey::alpha),
            EASE.optionalFieldOf("ease", Ease.LINEAR).forGetter(FadeKey::ease),
        ).apply(i, ::FadeKey)
    }

    val FOG_KEY: Codec<FogKey> = RecordCodecBuilder.create { i ->
        i.group(
            Codec.INT.fieldOf("t").forGetter(FogKey::t),
            Codec.floatRange(0.05f, 16f).fieldOf("scale").forGetter(FogKey::scale),
            EASE.optionalFieldOf("ease", Ease.LINEAR).forGetter(FogKey::ease),
        ).apply(i, ::FogKey)
    }

    val SCENE: Codec<Scene> = RecordCodecBuilder.create { i ->
        i.group(
            CAMERA_KEY.listOf().optionalFieldOf("camera", emptyList()).forGetter(Scene::camera),
            ACTOR_KEY.listOf().optionalFieldOf("actor", emptyList()).forGetter(Scene::actor),
            FADE_KEY.listOf().optionalFieldOf("fade", emptyList()).forGetter(Scene::fade),
            FOG_KEY.listOf().optionalFieldOf("fog", emptyList()).forGetter(Scene::fog),
            Codec.intRange(0, Int.MAX_VALUE).optionalFieldOf("duration").forGetter { Optional.ofNullable(it.duration) },
            Codec.intRange(0, 1200).optionalFieldOf("handoff", 20).forGetter(Scene::handoff),
            Codec.BOOL.optionalFieldOf("absolute", false).forGetter(Scene::absolute),
            Codec.BOOL.optionalFieldOf("letterbox", true).forGetter(Scene::letterbox),
            Codec.BOOL.optionalFieldOf("show_actor", true).forGetter(Scene::showActor),
        ).apply(i) { camera, actor, fade, fog, duration, handoff, absolute, letterbox, showActor ->
            Scene(camera, actor, fade, fog, duration.orElse(null), handoff, absolute, letterbox, showActor)
        }
    }
}
