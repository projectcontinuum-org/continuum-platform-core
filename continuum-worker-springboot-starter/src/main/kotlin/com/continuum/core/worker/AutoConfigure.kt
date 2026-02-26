package com.continuum.core.worker

import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.temporal.common.converter.DefaultDataConverter
import io.temporal.common.converter.GlobalDataConverter
import io.temporal.common.converter.JacksonJsonPayloadConverter
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration

@Configuration
@ComponentScan
class AutoConfigure {
  init {
    registerKotlinMapper()
  }
}

fun registerKotlinMapper() {
  val mapper = JacksonJsonPayloadConverter.newDefaultObjectMapper()
  val km = KotlinModule.Builder().build()
  mapper.registerModule(km)
  val jacksonConverter = JacksonJsonPayloadConverter(mapper)
  val dataConverter = DefaultDataConverter.newDefaultInstance()
    .withPayloadConverterOverrides(jacksonConverter)
  GlobalDataConverter.register(dataConverter)
}