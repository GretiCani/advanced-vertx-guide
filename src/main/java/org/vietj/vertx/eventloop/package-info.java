/**
 * = Demystifying the Event Loop
 * Julien Viet <julien@julienviet.com>
 *
 * The event loop plays a key role in Vert.x for writing highly scalable and performant network applications.
 * It is inherited from the Netty library on which Vert.x is based.
 *
 * We often use the expression _running on the event loop_, it has a very specific meaning: it means that the
 * current Thread is an event loop thread.
 *
 * == The golden rule
 *
 * When using Vert.x there is one Vert.x golden rule to respect:
 *
 * [quote, Tim Fox]
 * Never block the event loop!
 *
 * The code executed on the event loop should never block the event loop, for instance:
 *
 * - using a blocking method directly or not, for instance reading a file with the `java.io.FileInputStream` api
 *   or a a JDBC connection.
 * - doing a long and CPU intensive task
 *
 * When the event loop is blocked:
 *
 * [source,java]
 * ----
 * {@link org.vietj.vertx.eventloop.BlockingEventLoop#main}
 * ----
 *
 * Vert.x will detect it and log a warn:
 *
 * ----
 * WARNING: Thread Thread[vert.x-eventloop-thread-1,5,main] has been blocked for 2616 ms time 2000000000
 * Apr 04, 2015 1:18:43 AM io.vertx.core.impl.BlockedThreadChecker
 * WARNING: Thread Thread[vert.x-eventloop-thread-1,5,main] has been blocked for 3617 ms time 2000000000
 * Apr 04, 2015 1:18:44 AM io.vertx.core.impl.BlockedThreadChecker
 * WARNING: Thread Thread[vert.x-eventloop-thread-1,5,main] has been blocked for 4619 ms time 2000000000
 * java.lang.Thread.sleep(Native Method)
 * Apr 04, 2015 1:18:45 AM io.vertx.core.impl.BlockedThreadChecker
 * WARNING: Thread Thread[vert.x-eventloop-thread-1,5,main] has been blocked for 5620 ms time 2000000000
 * io.vertx.example.BlockingEventLoop.start(BlockingEventLoop.java:19)
 * io.vertx.core.AbstractVerticle.start(AbstractVerticle.java:111)
 * io.vertx.core.impl.DeploymentManager.lambda$doDeploy$88(DeploymentManager.java:433)
 * io.vertx.core.impl.DeploymentManager$$Lambda$4/2141179775.handle(Unknown Source)
 * io.vertx.core.impl.ContextImpl.lambda$wrapTask$3(ContextImpl.java:263)
 * io.vertx.core.impl.ContextImpl$$Lambda$5/758013696.run(Unknown Source)
 * io.netty.util.concurrent.SingleThreadEventExecutor.runAllTasks(SingleThreadEventExecutor.java:380)
 * io.netty.channel.nio.NioEventLoop.run(NioEventLoop.java:357)
 * io.netty.util.concurrent.SingleThreadEventExecutor$2.run(SingleThreadEventExecutor.java:116)
 * java.lang.Thread.run(Thread.java:745)
 * ----
 *
 * == The Context
 *
 * Beyond the event loop, Vert.x defines the notion of context. The `io.vertx.core.Context` class provides the `runOnContext(Handler)` method,
 * this method should be used when the thread attached to the context needs to run a particular task.
 *
 * For instance, the context thread initiates a non Vert.x action, when this action ends it needs to do update some
 * state and it needs to be done with the context thread to guarantee that the state will be visible by the
 * context thread.
 *
 * [source,java]
 * ----
 * {@link org.vietj.vertx.eventloop.RunningOnContext#start()}
 * ----
 *
 * A `Vertx` instance provides several methods for interacting with contexts.
 *
 * - The static `Vertx.currentContext()` methods returns the current context if there is one, it returns false otherwise.
 * - The `vertx.getOrCreateContext()` returns the context associated with the thread (like `currentContext`) otherwise
 *   it creates a new context, associates it to event loop and returns it.
 * - The `vertx.runOnContext(Handler<Void>)` method calls the `getOrCreateContext` method and schedule a task for
 *   execution via the `context.runOnContext(Handler<Void>)` method.
 *
 * There are three kinds of contexts.
 *
 * - Event loop context
 * - Worker context
 * - Multithreaded worker context
 *
 * === Event loop context
 *
 * An event loop context is a context that executes actions on an event loop. That's the most usual kind of context
 * used in Vert.x and the one provided by Vert.x when you create a context, unless a specific settings specify
 * a different kind of context:
 *
 * - Deploying a Verticle with the default configuration creates an event loop context and assigns it to this
 *   verticle
 * - Calling `Vertx.getOrCreateContext()` when no context already exists, for instance a _main_ method
 * - Creating a timer, server or client, when no context already exists, implicitely creates a context
 *
 * When Vert.x creates an event loop context, it choses an event loop for this context, the event loop is chosen via a round
 * robin algorithm:
 *
 * [source,java]
 * ----
 * {@link org.vietj.vertx.eventloop.CreatingEventLoopsFromMain#main}
 * ----
 *
 * The result is
 *
 * ----
 * Thread[main,5,main]
 * 0:Thread[vert.x-eventloop-thread-0,5,main]
 * 11:Thread[vert.x-eventloop-thread-11,5,main]
 * 10:Thread[vert.x-eventloop-thread-10,5,main]
 * 13:Thread[vert.x-eventloop-thread-13,5,main]
 * 12:Thread[vert.x-eventloop-thread-12,5,main]
 * 14:Thread[vert.x-eventloop-thread-14,5,main]
 * 16:Thread[vert.x-eventloop-thread-0,5,main]
 * 6:Thread[vert.x-eventloop-thread-6,5,main]
 * 15:Thread[vert.x-eventloop-thread-15,5,main]
 * 5:Thread[vert.x-eventloop-thread-5,5,main]
 * 4:Thread[vert.x-eventloop-thread-4,5,main]
 * 3:Thread[vert.x-eventloop-thread-3,5,main]
 * 2:Thread[vert.x-eventloop-thread-2,5,main]
 * 1:Thread[vert.x-eventloop-thread-1,5,main]
 * 17:Thread[vert.x-eventloop-thread-1,5,main]
 * 18:Thread[vert.x-eventloop-thread-2,5,main]
 * 19:Thread[vert.x-eventloop-thread-3,5,main]
 * 9:Thread[vert.x-eventloop-thread-9,5,main]
 * 8:Thread[vert.x-eventloop-thread-8,5,main]
 * 7:Thread[vert.x-eventloop-thread-7,5,main]
 * ----
 *
 * After sorting the result:
 *
 * ----
 * Thread[main,5,main]
 * 0:Thread[vert.x-eventloop-thread-0,5,main]
 * 1:Thread[vert.x-eventloop-thread-1,5,main]
 * 2:Thread[vert.x-eventloop-thread-2,5,main]
 * 3:Thread[vert.x-eventloop-thread-3,5,main]
 * 4:Thread[vert.x-eventloop-thread-4,5,main]
 * 5:Thread[vert.x-eventloop-thread-5,5,main]
 * 6:Thread[vert.x-eventloop-thread-6,5,main]
 * 7:Thread[vert.x-eventloop-thread-7,5,main]
 * 8:Thread[vert.x-eventloop-thread-8,5,main]
 * 9:Thread[vert.x-eventloop-thread-9,5,main]
 * 10:Thread[vert.x-eventloop-thread-10,5,main]
 * 11:Thread[vert.x-eventloop-thread-11,5,main]
 * 12:Thread[vert.x-eventloop-thread-12,5,main]
 * 13:Thread[vert.x-eventloop-thread-13,5,main]
 * 14:Thread[vert.x-eventloop-thread-14,5,main]
 * 15:Thread[vert.x-eventloop-thread-15,5,main]
 * 16:Thread[vert.x-eventloop-thread-0,5,main]
 * 17:Thread[vert.x-eventloop-thread-1,5,main]
 * 18:Thread[vert.x-eventloop-thread-2,5,main]
 * 19:Thread[vert.x-eventloop-thread-3,5,main]
 * ----
 *
 *
 * As we can see we obtained different event loop threads when running on context and the thread are obtained with
 * a round robin policy. Note that the number of event loop threads by default depends on your CPU but this can
 * be configured.
 *
 * An event loop context guarantees to always use the same thread, however the converse is not true: the same thread
 * can be used by different event loop contexts. The previous example shows clearly that a same thread is used
 * for different event loops by the Round Robin policy.
 *
 * === Worker context
 *
 * todo
 *
 * === Multithreaded event loop context
 *
 * todo
 *
 * == Configuring the event loop
 *
 * todo : talk about the options for configuring the event loop size, etc...
 *
 * == Verticles
 *
 * Vert.x guarantees that the same Verticle will always be called from the same thread, whether or not the Verticle
 * is deployed as a worker or not. This implies that any service created from a Verticle will reuse the same context,
 * for instance:
 *
 * - Creating a server
 * - Creating a client
 * - Creating a timer
 * - Registering an event but handler
 *
 * Such _services_ will call back the Verticle that created them at some point, when this happens it will be with
 * the *exact same thread*.
 *
 * == Embedding Vert.x
 *
 * When Vert.x is embedded like in a _main_ Java method, the thread creating Vert.x can be any kind of thread, but
 * it is certainly not a Vert.x thread. Any action that requires a context will implicitely create a context for
 * achieving this action.
 *
 * [source,java]
 * ----
 * {@link org.vietj.vertx.eventloop.CreatingAnEventLoopFromHttpServer#main}
 * ----
 *
 * When several actions are done, there will use different context and there are high chances they will use a
 * different event loop thread.
 *
 * [source,java]
 * ----
 * {@link org.vietj.vertx.eventloop.CreatingDifferentEventLoopsFromHttpServers#main}
 * ----
 *
 * The `numberOfServerStarted` field update is not safe since we may use a different thread. When the same
 * context needs to be used then the actions can be grouped with a `runOnContext` call:
 *
 * [source,java]
 * ----
 * {@link org.vietj.vertx.eventloop.UsingEventLoopsFromHttpServers#main}
 * ----
 *
 * Now we are sure that `numberOfServerStarted` will be safely updated.
 *
 * == Blocking
 *
 * todo.
 *
 */
@Document(fileName = "Demystifying_the_event_loop.adoc")
package org.vietj.vertx.eventloop;

import io.vertx.docgen.Document;