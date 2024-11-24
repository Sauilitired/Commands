//
// MIT License
//
// Copyright (c) 2024 Incendo
//
// Permission is hereby granted, free of charge, to any person obtaining a copy
// of this software and associated documentation files (the "Software"), to deal
// in the Software without restriction, including without limitation the rights
// to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
// copies of the Software, and to permit persons to whom the Software is
// furnished to do so, subject to the following conditions:
//
// The above copyright notice and this permission notice shall be included in all
// copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
// IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
// FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
// AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
// LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
// OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
// SOFTWARE.
//
package org.incendo.cloud.kotlin.coroutines.annotations

import io.leangen.geantyref.GenericTypeReflector
import io.leangen.geantyref.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.future.future
import org.incendo.cloud.annotations.AnnotationParser
import org.incendo.cloud.annotations.MethodCommandExecutionHandler
import org.incendo.cloud.annotations.method.AnnotatedMethodHandler
import org.incendo.cloud.annotations.method.ParameterValue
import org.incendo.cloud.annotations.parser.MethodArgumentParserFactory
import org.incendo.cloud.annotations.suggestion.MethodSuggestionProvider
import org.incendo.cloud.annotations.suggestion.SuggestionProviderFactory
import org.incendo.cloud.context.CommandContext
import org.incendo.cloud.context.CommandInput
import org.incendo.cloud.injection.ParameterInjectorRegistry
import org.incendo.cloud.parser.ArgumentParseResult
import org.incendo.cloud.parser.ArgumentParser.FutureArgumentParser
import org.incendo.cloud.parser.ParserDescriptor
import org.incendo.cloud.suggestion.Suggestion
import org.incendo.cloud.suggestion.SuggestionProvider
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Parameter
import java.util.concurrent.CompletableFuture
import java.util.function.Predicate
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.full.callSuspend
import kotlin.reflect.full.callSuspendBy
import kotlin.reflect.full.instanceParameter
import kotlin.reflect.full.valueParameters
import kotlin.reflect.jvm.javaType
import kotlin.reflect.jvm.jvmErasure
import kotlin.reflect.jvm.kotlinFunction
import kotlin.reflect.typeOf

/**
 * Adds coroutine support to the [AnnotationParser].
 *
 * @param scope coroutine scope
 * @param context coroutine context
 * @param onlyForSuspending whether the Kotlin execution handler should only be used for suspending functions
 * @return annotation parser
 */
public fun <C> AnnotationParser<C>.installCoroutineSupport(
    scope: CoroutineScope = GlobalScope,
    context: CoroutineContext = EmptyCoroutineContext,
    onlyForSuspending: Boolean = false
): AnnotationParser<C> {
    val predicate = Predicate<Method> { method ->
        if (onlyForSuspending) {
            method.kotlinFunction?.isSuspend == true
        } else {
            method.kotlinFunction != null
        }
    }
    registerCommandExecutionMethodFactory(predicate) {
        KotlinMethodCommandExecutionHandler(scope, context, it)
    }

    suggestionProviderFactory(KotlinSuggestionProviderFactory(scope, context))

    methodArgumentParserFactory(KotlinMethodArgumentParserFactory(scope, context, manager().parameterInjectorRegistry()))

    return this
}

private class KotlinMethodCommandExecutionHandler<C>(
    private val coroutineScope: CoroutineScope,
    private val coroutineContext: CoroutineContext,
    context: CommandMethodContext<C>
) : MethodCommandExecutionHandler<C>(context) {

    private val paramsWithoutContinuation = parameters()
        .filterNot { Continuation::class.java == it.type }
        .toTypedArray()

    override fun executeFuture(commandContext: CommandContext<C>): CompletableFuture<Void?> {
        val instance = context().instance()
        val kFunction = requireNotNull(context().method().kotlinFunction)
        return executeSuspendFunction(
            coroutineScope,
            coroutineContext,
            kFunction,
            instance,
            paramsWithoutContinuation,
            commandContext
        )
            .thenApply { null }
    }
}

private class KotlinSuggestionProviderFactory<C>(
    private val coroutineScope: CoroutineScope,
    private val coroutineContext: CoroutineContext
) : SuggestionProviderFactory<C> {

    override fun createSuggestionProvider(
        instance: Any,
        method: Method,
        injectorRegistry: ParameterInjectorRegistry<C>
    ): SuggestionProvider<C> {
        if (method.kotlinFunction == null) {
            return SuggestionProviderFactory.defaultFactory<C>().createSuggestionProvider(instance, method, injectorRegistry)
        }
        val kFunction = requireNotNull(method.kotlinFunction)
        return KotlinSuggestionProvider(coroutineScope, coroutineContext, kFunction, instance)
    }
}

private class KotlinSuggestionProvider<C>(
    private val coroutineScope: CoroutineScope,
    private val coroutineContext: CoroutineContext,
    private val kFunction: KFunction<*>,
    private val instance: Any
) : SuggestionProvider<C> {

    override fun suggestionsFuture(context: CommandContext<C>, input: CommandInput): CompletableFuture<Iterable<Suggestion>> {
        return coroutineScope.future(coroutineContext) {
            try {
                if (kFunction.valueParameters.isEmpty()) {
                    kFunction.callSuspend(instance)
                } else if (kFunction.valueParameters[1].type.classifier == String::class) {
                    kFunction.callSuspend(instance, context, input.lastRemainingToken())
                } else {
                    kFunction.callSuspend(instance, context, input)
                }
            } catch (e: InvocationTargetException) {
                e.cause?.let { throw it } ?: throw e // if cause exists, throw, else rethrow invocation exception
            }
        }.thenCompose { result ->
            when (result) {
                null -> CompletableFuture.completedFuture(emptyList<Suggestion>())
                is Sequence<*> -> MethodSuggestionProvider.mapSuggestions(result.asIterable())
                else -> MethodSuggestionProvider.mapSuggestions(result)
            }
        }
    }
}

private class KotlinMethodArgumentParserFactory<C>(
    private val coroutineScope: CoroutineScope,
    private val coroutineContext: CoroutineContext,
    private val parameterInjectorRegistry: ParameterInjectorRegistry<C>
) : MethodArgumentParserFactory<C> {
    private val defaultFactory = MethodArgumentParserFactory.defaultFactory<C>()
    private val argumentParseResultType = typeOf<ArgumentParseResult<*>>()

    override fun createArgumentParser(
        suggestionProvider: SuggestionProvider<C>,
        instance: Any,
        method: Method,
        injectorRegistry: ParameterInjectorRegistry<C>
    ): ParserDescriptor<C, *> {
        if (method.kotlinFunction == null) {
            return defaultFactory.createArgumentParser(
                suggestionProvider,
                instance,
                method,
                injectorRegistry
            )
        } else {
            val kFunction = requireNotNull(method.kotlinFunction)
            val returnType = kFunction.returnType
            val type = returnType.javaType
            val returnTypeToken: TypeToken<*>
            if (returnType.jvmErasure == argumentParseResultType.jvmErasure) {
                val arguments = returnType.arguments
                check(arguments.isNotEmpty()) {
                    "Argument type is not specified"
                }
                val actualType = arguments[0].type
                    ?: throw IllegalArgumentException("Argument type is not specified")
                returnTypeToken = TypeToken.get(actualType.javaType)
            } else {
                returnTypeToken = TypeToken.get(type)
            }
            return ParserDescriptor.of(
                KotlinMethodArgumentParser(
                    coroutineScope,
                    coroutineContext,
                    kFunction,
                    instance,
                    suggestionProvider,
                    method,
                    parameterInjectorRegistry
                ),
                returnTypeToken
            )
        }
    }
}

private class KotlinMethodArgumentParser<C, T>(
    private val coroutineScope: CoroutineScope,
    private val coroutineContext: CoroutineContext,
    private val kFunction: KFunction<*>,
    private val instance: Any,
    private val suggestionProvider: SuggestionProvider<C>,
    javaMethod: Method,
    parameterInjectorRegistry: ParameterInjectorRegistry<C>
) : FutureArgumentParser<C, T>, AnnotatedMethodHandler<C>(javaMethod, instance, parameterInjectorRegistry) {

    private val paramsWithoutContinuation = parameters()
        .filterNot { Continuation::class.java == it.type }
        .toTypedArray()

    override fun parseFuture(
        commandContext: CommandContext<C>,
        commandInput: CommandInput
    ): CompletableFuture<ArgumentParseResult<T>> =
        executeSuspendFunction(
            coroutineScope,
            coroutineContext,
            kFunction,
            instance,
            paramsWithoutContinuation,
            commandContext,
            listOf(commandInput)
        )
            .mapResult()

    override fun suggestionProvider(): SuggestionProvider<C> = suggestionProvider

    @Suppress("UNCHECKED_CAST")
    private fun <T> CompletableFuture<*>.mapResult(): CompletableFuture<ArgumentParseResult<T>> =
        handle { result, exception ->
            if (exception != null) {
                ArgumentParseResult.failure(exception)
            } else {
                when (result) {
                    null -> ArgumentParseResult.failure(IllegalArgumentException("Result not found"))
                    is ArgumentParseResult<*> -> result as ArgumentParseResult<T>
                    else -> ArgumentParseResult.success((result as T)!!)
                }
            }
        }
}

private fun <C> AnnotatedMethodHandler<C>.executeSuspendFunction(
    coroutineScope: CoroutineScope,
    coroutineContext: CoroutineContext,
    kFunction: KFunction<*>,
    instance: Any,
    paramsWithoutContinuation: Array<Parameter>,
    commandContext: CommandContext<C>,
    preDefinedParameters: List<Any> = emptyList()
): CompletableFuture<*> {
    val valueParameters = kFunction.valueParameters.associateBy(KParameter::name)

    if (valueParameters.values.none(KParameter::isOptional)) {
        val params = createParameterValues(
            commandContext,
            paramsWithoutContinuation,
            preDefinedParameters
        ).map(ParameterValue::value)

        return coroutineScope.future(coroutineContext) {
            try {
                kFunction.callSuspend(instance, *params.toTypedArray())
            } catch (e: InvocationTargetException) {
                e.cause?.let { throw it } ?: throw e
            }
        }
    }

    val instanceParameter = kFunction.instanceParameter?.let { mapOf(it to instance) } ?: emptyMap()

    val params = createParameterValues(
        commandContext,
        paramsWithoutContinuation,
        preDefinedParameters
    ).associate { parameterValue ->
        val descriptor = parameterValue.descriptor()
        val parameter = if (parameterValue.parameter().name in valueParameters) {
            requireNotNull(valueParameters[parameterValue.parameter().name])
        } else if (descriptor != null && descriptor.name() in valueParameters) {
            requireNotNull(valueParameters[descriptor.name()])
        } else {
            requireNotNull(valueParameters.values.firstOrNull { it.hasType(parameterValue.parameter().type) }) {
                "could not find parameter ${parameterValue.parameter().name} of type ${parameterValue.parameter().type}, " +
                    "kParameters: ${valueParameters.entries.joinToString { "${it.key} (${it.value.type.javaType})" }}"
            }
        }
        parameter to parameterValue.value()
    }.filter { (parameter, value) ->
        value != null || (!parameter.isOptional)
    } + instanceParameter

    return coroutineScope.future(coroutineContext) {
        try {
            kFunction.callSuspendBy(params)
        } catch (e: InvocationTargetException) {
            e.cause?.let { throw it } ?: throw e
        }
    }
}

private fun KParameter.hasType(clazz: Class<*>): Boolean =
    GenericTypeReflector.erase(type.javaType) == clazz
