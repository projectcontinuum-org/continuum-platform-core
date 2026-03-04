package com.continuum.feature.ai

import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration

/**
 * Spring Boot auto-configuration for the Continuum AI Feature module.
 *
 * This configuration class is automatically loaded by Spring Boot's auto-configuration
 * mechanism when the `continuum-feature-ai` dependency is included in a project.
 *
 * ## Auto-configured Components
 * - [UnslothTrainerNodeModel][com.continuum.feature.ai.node.UnslothTrainerNodeModel] - LLM fine-tuning node
 *
 * ## Usage
 * Simply include the `continuum-feature-ai` dependency in your project:
 *
 * ```kotlin
 * dependencies {
 *     implementation(project(":continuum-feature-ai"))
 * }
 * ```
 *
 * The AI nodes will be automatically registered and available in the workflow editor.
 *
 * @author Continuum Team
 * @since 1.0.0
 */
@Configuration
@ComponentScan(basePackages = ["com.continuum.feature.ai"])
class AutoConfigure

