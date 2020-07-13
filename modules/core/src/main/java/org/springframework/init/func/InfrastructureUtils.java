/*
 * Copyright 2020-2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.init.func;

import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.config.SingletonBeanRegistry;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.support.GenericApplicationContext;

/**
 * @author Dave Syer
 *
 */
public class InfrastructureUtils {

	private static final String CONTEXT_NAME = GenericApplicationContext.class.getName();

	public static GenericApplicationContext getContext(SingletonBeanRegistry beans) {
		GenericApplicationContext context = (GenericApplicationContext) beans.getSingleton(CONTEXT_NAME);
		return context;
	}

	public static <T> T getBean(SingletonBeanRegistry beans, String name, Class<T> type) {
		GenericApplicationContext context = (GenericApplicationContext) beans.getSingleton(CONTEXT_NAME);
		if (!context.isActive() && context.getBeanFactory().containsSingleton(name)) {
			@SuppressWarnings("unchecked")
			T result = (T) context.getBeanFactory().getSingleton(name);
			return result;
		}
		return context.getBean(name, type);
	}

	public static <T> T getBean(SingletonBeanRegistry beans, Class<T> type) {
		GenericApplicationContext context = (GenericApplicationContext) beans.getSingleton(CONTEXT_NAME);
		if (!context.isActive() && context.getBeanFactory().containsSingleton(type.getName())) {
			@SuppressWarnings("unchecked")
			T result = (T) context.getBeanFactory().getSingleton(type.getName());
			return result;
		}
		return context.getBean(type);
	}

	public static void install(SingletonBeanRegistry beans, GenericApplicationContext context) {
		if (!beans.containsSingleton(CONTEXT_NAME)) {
			beans.registerSingleton(CONTEXT_NAME, context);
		}
	}

	public static boolean isInstalled(SingletonBeanRegistry beans) {
		return beans.containsSingleton(CONTEXT_NAME);
	}

	public static <T> T getOrCreate(GenericApplicationContext main, String name, Class<T> type) {
		GenericApplicationContext context = getContext(main.getBeanFactory());
		if (context.getBeanNamesForType(type, false, true).length == 0) {
			// Can't use beans to do this because it probably isn't active yet
			T bean = context.getAutowireCapableBeanFactory().createBean(type);
			if (bean instanceof BeanFactoryAware) {
				((BeanFactoryAware) bean).setBeanFactory(main.getBeanFactory());
			}
			if (bean instanceof ApplicationContextAware) {
				((ApplicationContextAware) bean).setApplicationContext(main);
			}
			if (bean instanceof EnvironmentAware) {
				((EnvironmentAware) bean).setEnvironment(main.getEnvironment());
			}
			context.getBeanFactory().registerSingleton(name, bean);
			// System.err.println(type);
		}
		return context.getBean(type);
	}

	public static <T> T getOrCreate(GenericApplicationContext main, Class<T> initializerType) {
		return getOrCreate(main, initializerType.getName(), initializerType);
	}

	public static Object getOrCreate(GenericApplicationContext main, String type) {
		GenericApplicationContext context = getContext(main.getBeanFactory());
		TypeService types = context.getBean(TypeService.class);
		Class<?> clazz = types.getType(type);
		return getOrCreate(main, type, clazz);
	}

	public static boolean containsBean(SingletonBeanRegistry beans, String name) {
		return getContext(beans).containsBean(name);
	}

	public static boolean containsBean(SingletonBeanRegistry beans, Class<?> type) {
		return getContext(beans).getBeanFactory().containsSingleton(type.getName())
				|| getContext(beans).getBeanNamesForType(type, false, false).length > 0;
	}

	public static GenericApplicationContext preInstall(SingletonBeanRegistry beans) {
		GenericApplicationContext context = new GenericApplicationContext();
		install(beans, context);
		// Not refreshed or initialized...
		return context;
	}

}
