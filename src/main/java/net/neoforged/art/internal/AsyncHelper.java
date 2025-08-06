/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.art.internal;


import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

class AsyncHelper {
    private final ExecutorService exec;

    AsyncHelper(ExecutorService exec) {
        this.exec = exec;
    }

    public <I> void consumeAll(Collection<? extends I> inputs, Function<I, String> namer, Consumer<I> consumer) {
        Function<I, Pair<String, Callable<Void>>> toCallable = i -> new Pair<>(namer.apply(i), () -> {
            consumer.accept(i);
            return null;
        });
        invokeAll(inputs.stream().map(toCallable).collect(Collectors.toList()));
    }

    public <I> CompletableFuture<Void> submitConsumeAll(Collection<? extends I> inputs, Function<I, String> namer, Consumer<I> consumer) {
        int futureIndex = 0;
        CompletableFuture<?>[] futures = new CompletableFuture<?>[inputs.size()];
        for (I input : inputs) {
            futures[futureIndex++] = CompletableFuture.runAsync(() -> {
                try {
                    consumer.accept(input);
                } catch (Exception e) {
                    String inputName = namer.apply(input);
                    throw new RuntimeException("Failed to execute task " + inputName, e);
                }
            }, exec);
        }

        return CompletableFuture.allOf(futures);
    }

    public <I, O> List<O> invokeAll(Collection<? extends I> inputs, Function<I, String> namer, Function<I, O> converter) {
        Function<I, Pair<String, Callable<O>>> toCallable = i -> new Pair<>(namer.apply(i), () -> converter.apply(i));
        return invokeAll(inputs.stream().map(toCallable).collect(Collectors.toList()));
    }

    public <O> List<O> invokeAll(Collection<Pair<String, ? extends Callable<O>>> tasks) {
        List<O> ret = new ArrayList<>(tasks.size());
        List<Pair<String, Future<O>>> processed = new ArrayList<>(tasks.size());
        for (Pair<String, ? extends Callable<O>> task : tasks) {
            processed.add(new Pair<>(task.getLeft(), exec.submit(task.getRight())));
        }
        for (Pair<String, Future<O>> future : processed) {
            try {
                O done = future.getRight().get();
                if (done != null)
                    ret.add(done);
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException("Failed to execute task " + future.getLeft(), e);
            }
        }
        return ret;
    }

    public <I, O> CompletableFuture<List<O>> submitInvokeAll(List<? extends I> inputs, Function<I, String> namer, Function<I, O> converter) {
        List<O> ret = new ArrayList<>(inputs.size());
        for (int i = 0; i < inputs.size(); i++) {
            ret.add(null);
        }

        CompletableFuture<?>[] futures = new CompletableFuture<?>[inputs.size()];
        for (int i = 0; i < inputs.size(); i++) {
            int taskIndex = i;
            I input = inputs.get(i);
            futures[i] = CompletableFuture.runAsync(() -> {
                try {
                    ret.set(taskIndex, converter.apply(input));
                } catch (Exception e) {
                    String inputName = namer.apply(input);
                    throw new RuntimeException("Failed to execute task " + inputName, e);
                }
            }, exec);
        }

        return CompletableFuture.allOf(futures).thenApply(ignored -> {
            ret.removeIf(Objects::isNull);
            return ret;
        });
    }

    public <O> CompletableFuture<List<O>> submitInvokeAll(List<Pair<String, ? extends Callable<O>>> tasks) {
        List<O> ret = new ArrayList<>(tasks.size());
        for (int i = 0; i < tasks.size(); i++) {
            ret.add(null);
        }

        CompletableFuture<?>[] futures = new CompletableFuture<?>[tasks.size()];
        for (int i = 0; i < tasks.size(); i++) {
            int taskIndex = i;
            futures[i] = CompletableFuture.runAsync(() -> {
                try {
                    ret.set(taskIndex, tasks.get(taskIndex).getRight().call());
                } catch (Exception e) {
                    String taskDescription = tasks.get(taskIndex).getLeft();
                    throw new RuntimeException("Failed to execute task " + taskDescription, e);
                }
            }, exec);
        }

        return CompletableFuture.allOf(futures).thenApply(ignored -> {
            ret.removeIf(Objects::isNull);
            return ret;
        });
    }

}
