/*
 * Copyright 2019 Björn Kautler
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.kautler.command.handler;

import net.kautler.command.Internal;
import net.kautler.command.api.Command;
import net.kautler.command.api.CommandHandler;
import net.kautler.command.api.event.javacord.CommandNotAllowedEventJavacord;
import net.kautler.command.api.event.javacord.CommandNotFoundEventJavacord;
import net.kautler.command.api.prefix.PrefixProvider;
import net.kautler.command.api.restriction.Restriction;
import org.apache.logging.log4j.Logger;
import org.javacord.api.DiscordApi;
import org.javacord.api.entity.message.Message;
import org.javacord.api.event.message.MessageCreateEvent;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Event;
import javax.enterprise.inject.Instance;
import javax.enterprise.inject.Produces;
import javax.inject.Inject;
import java.util.Collection;
import java.util.stream.Stream;

import static java.util.concurrent.CompletableFuture.runAsync;

/**
 * A command handler that handles Javacord messages.
 */
@ApplicationScoped
class CommandHandlerJavacord extends CommandHandler<Message> {
    /**
     * The logger for this command handler.
     */
    @Inject
    @Internal
    private volatile Logger logger;

    /**
     * A {@code DiscordApi} {@link Produces produced} by the framework user if Javacord support should be used.
     * Alternatively a {@code Collection<DiscordApi>} could be produced, for example if sharding is used.
     */
    @Inject
    private volatile Instance<DiscordApi> discordApis;

    /**
     * A collection of {@code DiscordApi}s {@link Produces produced} by the framework user if Javacord support should
     * be used for example with sharding. Alternatively a single {@code DiscordApi} could be produced.
     */
    @Inject
    private volatile Instance<Collection<DiscordApi>> discordApiCollections;

    /**
     * A CDI event for firing command not allowed events.
     */
    @Inject
    private volatile Event<CommandNotAllowedEventJavacord> commandNotAllowedEvent;

    /**
     * A CDI event for firing command not found events.
     */
    @Inject
    private volatile Event<CommandNotFoundEventJavacord> commandNotFoundEvent;

    /**
     * Constructs a new Javacord command handler.
     */
    private CommandHandlerJavacord() {
    }

    /**
     * Sets the available restrictions for this command handler.
     *
     * @param availableRestrictions the available restrictions for this command handler
     */
    @Inject
    private void setAvailableRestrictions(Instance<Restriction<? super Message>> availableRestrictions) {
        doSetAvailableRestrictions(availableRestrictions);
    }

    /**
     * Sets the commands for this command handler.
     *
     * @param commands the available commands for this command handler
     */
    @Inject
    private void setCommands(Instance<Command<? super Message>> commands) {
        doSetCommands(commands);
    }

    /**
     * Sets the custom prefix provider for this command handler.
     *
     * @param customPrefixProvider the custom prefix provider for this command handler
     */
    @Inject
    private void setCustomPrefixProvider(Instance<PrefixProvider<? super Message>> customPrefixProvider) {
        doSetCustomPrefixProvider(customPrefixProvider);
    }

    /**
     * Adds this command handler to the injected {@code DiscordApi} instances as message create listener.
     */
    @PostConstruct
    private void addListener() {
        if (discordApis.isUnsatisfied() && discordApiCollections.isUnsatisfied()) {
            logger.info("No DiscordApi or Collection<DiscordApi> injected, CommandHandlerJavacord will not be used.");
        } else {
            if (discordApis.isUnsatisfied()) {
                logger.info("Collection<DiscordApi> injected, CommandHandlerJavacord will be used.");
            } else if (discordApiCollections.isUnsatisfied()) {
                logger.info("DiscordApi injected, CommandHandlerJavacord will be used.");
            } else {
                logger.info("DiscordApi and Collection<DiscordApi> injected, CommandHandlerJavacord will be used.");
            }
            Stream.concat(
                    discordApis.stream(),
                    discordApiCollections.stream().flatMap(Collection::stream)
            ).forEach(discordApi -> discordApi.addMessageCreateListener(this::handleMessage));
        }
    }

    /**
     * Handles the actual messages received.
     *
     * @param messageCreateEvent the message create event
     */
    private void handleMessage(MessageCreateEvent messageCreateEvent) {
        Message message = messageCreateEvent.getMessage();
        doHandleMessage(message, message.getContent());
    }

    @Override
    protected void fireCommandNotAllowedEvent(Message message, String prefix, String usedAlias) {
        commandNotAllowedEvent.fireAsync(new CommandNotAllowedEventJavacord(message, prefix, usedAlias));
    }

    @Override
    protected void fireCommandNotFoundEvent(Message message, String prefix, String usedAlias) {
        commandNotFoundEvent.fireAsync(new CommandNotFoundEventJavacord(message, prefix, usedAlias));
    }

    @Override
    protected void executeAsync(Message message, Runnable commandExecutor) {
        runAsync(commandExecutor, message.getApi().getThreadPool().getExecutorService())
                .whenComplete((nothing, throwable) -> {
                    if (throwable != null) {
                        logger.error("Exception while executing command asynchronously", throwable);
                    }
                });
    }
}
