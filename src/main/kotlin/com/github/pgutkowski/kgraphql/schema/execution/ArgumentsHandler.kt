package com.github.pgutkowski.kgraphql.schema.execution

import com.github.pgutkowski.kgraphql.*
import com.github.pgutkowski.kgraphql.request.Arguments
import com.github.pgutkowski.kgraphql.request.Variables
import com.github.pgutkowski.kgraphql.request.VariablesJson
import com.github.pgutkowski.kgraphql.schema.DefaultSchema
import com.github.pgutkowski.kgraphql.schema.model.FunctionWrapper
import com.github.pgutkowski.kgraphql.schema.model.SchemaModel
import com.github.pgutkowski.kgraphql.schema.model.KQLType
import kotlin.reflect.KClass
import kotlin.reflect.KParameter
import kotlin.reflect.KType
import kotlin.reflect.full.createType
import kotlin.reflect.full.starProjectedType
import kotlin.reflect.full.withNullability
import kotlin.reflect.jvm.jvmErasure


internal class ArgumentsHandler(schema : DefaultSchema) : ArgumentTransformer(schema) {

    fun <T>transformArguments (
            funName: String,
            functionWrapper: FunctionWrapper<T>,
            args: Arguments?,
            variables: Variables
    ) : List<Any?>{
        val parameters = functionWrapper.valueParameters()
        val unsupportedArguments = args?.filter { arg -> parameters.none { parameter -> parameter.name == arg.key }}

        if(unsupportedArguments?.isNotEmpty() ?: false){
            throw SyntaxException("$funName does support arguments ${parameters.map { it.name }}. found arguments ${args?.keys}")
        }

        return parameters.map { parameter ->
            val value = args?.get(parameter.name)

            when {
                value == null && parameter.isNullable() -> null
                value == null && parameter.isNotNullable() -> {
                    throw IllegalArgumentException("argument ${parameter.name} is not optional, value cannot be null")
                }
                value is String -> {
                    val transformedValue = transformPropertyValue(parameter, value, variables)
                    if(transformedValue == null && parameter.isNotNullable()){
                        throw IllegalArgumentException("argument ${parameter.name} is not optional, value cannot be null")
                    }
                    transformedValue
                }
                value is List<*> && parameter.type.jvmErasure == List::class -> {
                    value.map { element ->
                        if(element is String){
                            transformCollectionElementValue(parameter, element, variables)
                        } else {
                            throw ExecutionException("Unexpected non-string list element")
                        }
                    }
                }
                value is List<*> && parameter.type.jvmErasure != List::class -> {
                    throw SyntaxException("Invalid list value passed to non-list argument")
                }
                else -> throw SyntaxException("Non string arguments are not supported yet")
            }
        }
    }
}