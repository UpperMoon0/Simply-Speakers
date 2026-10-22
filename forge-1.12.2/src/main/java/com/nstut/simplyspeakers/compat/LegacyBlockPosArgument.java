package com.nstut.simplyspeakers.compat;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/** Absolute, ~relative and ^local coordinates for the unchanged command tree. */
public final class LegacyBlockPosArgument implements ArgumentType<LegacyBlockPosArgument.Coordinates> {
    private static final SimpleCommandExceptionType MIXED = new SimpleCommandExceptionType(
            LegacyText.literal("Local coordinates cannot be mixed with world coordinates"));
    public static LegacyBlockPosArgument blockPos() { return new LegacyBlockPosArgument(); }

    public Coordinates parse(StringReader reader) throws CommandSyntaxException {
        double[] value = new double[3];
        boolean[] relative = new boolean[3];
        boolean local = reader.canRead() && reader.peek() == '^';
        for (int i = 0; i < 3; i++) {
            if (i > 0) {
                if (!reader.canRead() || reader.peek() != ' ')
                    throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.readerExpectedDouble().createWithContext(reader);
                reader.skip();
            }
            boolean caret = reader.canRead() && reader.peek() == '^';
            if (caret != local) throw MIXED.createWithContext(reader);
            relative[i] = reader.canRead() && (reader.peek() == '~' || caret);
            if (relative[i]) reader.skip();
            if (!reader.canRead() || reader.peek() == ' ') {
                if (!relative[i]) throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.readerExpectedDouble().createWithContext(reader);
                value[i] = 0;
            } else {
                value[i] = reader.readDouble();
                if (!Double.isFinite(value[i]))
                    throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.readerInvalidDouble().createWithContext(reader, value[i]);
            }
        }
        return new Coordinates(value, relative, local);
    }

    public static BlockPos getPos(CommandContext<LegacyCommandSource> context, String key) {
        Coordinates coordinate = context.getArgument(key, Coordinates.class);
        Vec3d base = context.getSource().sender.getPositionVector();
        if (!coordinate.local) return new BlockPos(
                coordinate.v[0] + (coordinate.r[0] ? base.x : 0),
                coordinate.v[1] + (coordinate.r[1] ? base.y : 0),
                coordinate.v[2] + (coordinate.r[2] ? base.z : 0));
        Entity entity = context.getSource().getEntity();
        double yaw = Math.toRadians(entity == null ? 0 : entity.rotationYaw);
        double pitch = Math.toRadians(entity == null ? 0 : entity.rotationPitch);
        Vec3d forward = new Vec3d(-Math.sin(yaw) * Math.cos(pitch), -Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch));
        Vec3d up = new Vec3d(Math.sin(yaw) * Math.sin(pitch), Math.cos(pitch), -Math.cos(yaw) * Math.sin(pitch));
        Vec3d left = up.crossProduct(forward);
        return new BlockPos(base.add(left.scale(coordinate.v[0])).add(up.scale(coordinate.v[1])).add(forward.scale(coordinate.v[2])));
    }

    public static final class Coordinates {
        final double[] v;
        final boolean[] r;
        final boolean local;
        Coordinates(double[] value, boolean[] relative, boolean local) { this.v = value; this.r = relative; this.local = local; }
    }
}
