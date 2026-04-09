package com.otectus.runicskills.common.command.arguments;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.network.chat.Component;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class SkillArgument implements ArgumentType<String> {

    private static final List<String> EXAMPLES = Arrays.asList("Strength", "Dexterity", "Intelligence", "Magic", "Constitution", "Endurance", "Building", "Wisdom", "Fortune", "Tinkering");
    public static final DynamicCommandExceptionType ERROR_UNKNOWN_TITLE;

    static {
        ERROR_UNKNOWN_TITLE = new DynamicCommandExceptionType(object -> Component.translatable("commands.argument.skill.not_found", object));
    }

    public static SkillArgument getArgument() {
        return new SkillArgument();
    }

    @Override
    public String parse(StringReader reader) throws CommandSyntaxException {
        return reader.readString();
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(final CommandContext<S> context, final SuggestionsBuilder builder) {
        if(builder.getRemaining().isEmpty()){
            EXAMPLES.forEach(builder::suggest);
            return builder.buildFuture();
        }

        EXAMPLES.stream().filter(str -> str.toLowerCase().contains(builder.getRemaining().toLowerCase())).forEach(builder::suggest);
        return builder.buildFuture();
    }

    @Override
    public Collection<String> getExamples() {
        return EXAMPLES;
    }
}
