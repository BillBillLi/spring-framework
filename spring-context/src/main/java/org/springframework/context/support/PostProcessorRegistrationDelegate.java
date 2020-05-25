/*
 * Copyright 2002-2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.context.support;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.config.DestructionAwareBeanPostProcessor;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.beans.factory.support.MergedBeanDefinitionPostProcessor;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ApplicationEventMulticaster;
import org.springframework.core.OrderComparator;
import org.springframework.core.Ordered;
import org.springframework.core.PriorityOrdered;

/**
 * Delegate for AbstractApplicationContext's post-processor handling.
 *
 * @author Juergen Hoeller
 * @since 4.0
 */
class PostProcessorRegistrationDelegate {

	public static void invokeBeanFactoryPostProcessors(
			ConfigurableListableBeanFactory beanFactory, List<BeanFactoryPostProcessor> beanFactoryPostProcessors) {
		// 已经实例化过的processor的集合
		Set<String> processedBeans = new HashSet<String>();
		
		// 首先调用BeanDefinitionRegistryPostProcessors
		// 因为参数中的beanFactory是DefaultListableBeanFactory的实例，而DefaultListableBeanFactory实现了
		// BeanDefinitionRegistry，因此，if的条件是true
		if (beanFactory instanceof BeanDefinitionRegistry) {
			BeanDefinitionRegistry registry = (BeanDefinitionRegistry) beanFactory;
			// regularPostProcessors 是放普通的BeanFactoryPostProcessor的
			List<BeanFactoryPostProcessor> regularPostProcessors = new LinkedList<BeanFactoryPostProcessor>();
			// 而这个registryPostProcessors是放BeanDefinitionRegistryPostProcessor的
			// 注意：BeanDefinitionRegistryPostProcessor接口继承了BeanFactoryPostProcessor
			List<BeanDefinitionRegistryPostProcessor> registryPostProcessors = new LinkedList<BeanDefinitionRegistryPostProcessor>();

			for (BeanFactoryPostProcessor postProcessor : beanFactoryPostProcessors) {
				// 如果BeanFactoryPostProcessor是BeanDefinitionRegistryPostProcessor，
				if (postProcessor instanceof BeanDefinitionRegistryPostProcessor) {
					BeanDefinitionRegistryPostProcessor registryPostProcessor =
							(BeanDefinitionRegistryPostProcessor) postProcessor;
					// 执行BeanDefinitionRegistryPostProcessor，并加入registryPostProcessors集合
					registryPostProcessor.postProcessBeanDefinitionRegistry(registry);
					registryPostProcessors.add(registryPostProcessor);
				// 否则只加入regularPostProcessors集合
				} else {
					regularPostProcessors.add(postProcessor);
				}
			}

			
			String[] postProcessorNames = beanFactory.getBeanNamesForType(BeanDefinitionRegistryPostProcessor.class, true, false);
			// 首先调用实现了 PriorityOrdered 的 BeanDefinitionRegistryPostProcessors
			List<BeanDefinitionRegistryPostProcessor> priorityOrderedPostProcessors = new ArrayList<BeanDefinitionRegistryPostProcessor>();
			for (String ppName : postProcessorNames) {
				if (beanFactory.isTypeMatch(ppName, PriorityOrdered.class)) {
					// 注意，这里实例化了BeanDefinitionRegistryPostProcessor
					priorityOrderedPostProcessors.add(beanFactory.getBean(ppName, BeanDefinitionRegistryPostProcessor.class));
					processedBeans.add(ppName);
				}
			}
			// 这个排序呢，其实是为了调整好那种既实现了PriorityOrdered，和Ordered的BeanDefinitionRegistryPostProcessor
			// 因为实现了PriorityOrdered也可以同时实现Ordered（虽然PriorityOrdered继承了Ordered，）在同时实现这两个接口的情况
			// 下，如果不排序，但是你的BeanDefinitionRegistryPostProcessor又需要这种执行顺序时候，在下面invoke的时候就会有问题
			// 接下来的排序也是这个道理
			OrderComparator.sort(priorityOrderedPostProcessors);
			// 把实现了 PriorityOrdered 的放到之前的 registryPostProcessors 中
			registryPostProcessors.addAll(priorityOrderedPostProcessors);
			// 执行实现了 PriorityOrdered 的 BeanDefinitionRegistryPostProcessor
			invokeBeanDefinitionRegistryPostProcessors(priorityOrderedPostProcessors, registry);

			// 然后调用实现了 Ordered 的 BeanDefinitionRegistryPostProcessors
			// 为什么要重新拿这个数组呢？因为实现了 PriorityOrdered 的 BeanDefinitionRegistryPostProcessor 可以给你注册了实现
			// Ordered 和什么排序接口都没有的 BeanDefinitionRegistryPostProcessor
			postProcessorNames = beanFactory.getBeanNamesForType(BeanDefinitionRegistryPostProcessor.class, true, false);
			List<BeanDefinitionRegistryPostProcessor> orderedPostProcessors = new ArrayList<BeanDefinitionRegistryPostProcessor>();
			for (String ppName : postProcessorNames) {
				// 执行过的跳过不执行
				if (!processedBeans.contains(ppName) && beanFactory.isTypeMatch(ppName, Ordered.class)) {
					orderedPostProcessors.add(beanFactory.getBean(ppName, BeanDefinitionRegistryPostProcessor.class));
					processedBeans.add(ppName);
				}
			}
			OrderComparator.sort(orderedPostProcessors);
			registryPostProcessors.addAll(orderedPostProcessors);
			invokeBeanDefinitionRegistryPostProcessors(orderedPostProcessors, registry);

			// 最后调用实现了其他的BeanDefinitionRegistryPostProcessors
			boolean reiterate = true;
			while (reiterate) {
				reiterate = false;
				postProcessorNames = beanFactory.getBeanNamesForType(BeanDefinitionRegistryPostProcessor.class, true, false);
				// 外层之所以是个while循环，是因为BeanDefinitionRegistryPostProcessor可以再给你
				// 注册个BeanDefinitionRegistryPostProcessor进来，所以要循环执行，只要你的for循环可以进来
				// reiterate这个是否循环的flag就是true
				for (String ppName : postProcessorNames) {
					if (!processedBeans.contains(ppName)) {
						BeanDefinitionRegistryPostProcessor pp = beanFactory.getBean(ppName, BeanDefinitionRegistryPostProcessor.class);
						registryPostProcessors.add(pp);
						processedBeans.add(ppName);
						pp.postProcessBeanDefinitionRegistry(registry);
						reiterate = true;
					}
				}
			}

			// 都是调用BeanFactoryPostProcessor的方法postProcessBeanFactory
			invokeBeanFactoryPostProcessors(registryPostProcessors, beanFactory);
			invokeBeanFactoryPostProcessors(regularPostProcessors, beanFactory);
		}else {
			invokeBeanFactoryPostProcessors(beanFactoryPostProcessors, beanFactory);
		}

		// 这里有个很大的注意点哦：上面的逻辑都是在调用入参的BeanFactoryPostProcessor和BeanDefinitionRegistryPostProcessor
		// 而接下来的是调用beanFactory容器中的BeanFactoryPostProcessor，逻辑和上面类似，也是按实现PriorityOrdered，Ordered
		// 和普通的来调用。至于为什么要先调用BeanDefinitionRegistryPostProcessors然后调用BeanFactoryPostProcessor呢？这是因为
		// BeanDefinitionRegistryPostProcessors也可以注册新的BeanFactoryPostProcessor进来
		
		String[] postProcessorNames = beanFactory.getBeanNamesForType(BeanFactoryPostProcessor.class, true, false);
		
		// 三个优先级的BeanFactoryPostProcessor的集合
		List<BeanFactoryPostProcessor> priorityOrderedPostProcessors = new ArrayList<BeanFactoryPostProcessor>();
		List<String> orderedPostProcessorNames = new ArrayList<String>();
		List<String> nonOrderedPostProcessorNames = new ArrayList<String>();
		for (String ppName : postProcessorNames) {
			// 如果已经被解析过则跳过
			if (processedBeans.contains(ppName)) {
				
			// PriorityOrdered加进高优先级的集合，并优先初始化
			} else if (beanFactory.isTypeMatch(ppName, PriorityOrdered.class)) {
				priorityOrderedPostProcessors.add(beanFactory.getBean(ppName, BeanFactoryPostProcessor.class));
			// Ordered的加进一般优先级集合
			} else if (beanFactory.isTypeMatch(ppName, Ordered.class)) {
				orderedPostProcessorNames.add(ppName);
			// 加入普通集合
			} else {
				nonOrderedPostProcessorNames.add(ppName);
			}
		}

		// 排序并调用高优先级的BeanFactoryPostProcessor
		OrderComparator.sort(priorityOrderedPostProcessors);
		invokeBeanFactoryPostProcessors(priorityOrderedPostProcessors, beanFactory);

		// 初始化一般优先级BeanFactoryPostProcessor并排序调用
		List<BeanFactoryPostProcessor> orderedPostProcessors = new ArrayList<BeanFactoryPostProcessor>();
		for (String postProcessorName : orderedPostProcessorNames) {
			orderedPostProcessors.add(beanFactory.getBean(postProcessorName, BeanFactoryPostProcessor.class));
		}
		OrderComparator.sort(orderedPostProcessors);
		invokeBeanFactoryPostProcessors(orderedPostProcessors, beanFactory);

		// 初始化普通的BeanFactoryPostProcessor并排序调用
		List<BeanFactoryPostProcessor> nonOrderedPostProcessors = new ArrayList<BeanFactoryPostProcessor>();
		for (String postProcessorName : nonOrderedPostProcessorNames) {
			nonOrderedPostProcessors.add(beanFactory.getBean(postProcessorName, BeanFactoryPostProcessor.class));
		}
		invokeBeanFactoryPostProcessors(nonOrderedPostProcessors, beanFactory);
	}

	public static void registerBeanPostProcessors(
			ConfigurableListableBeanFactory beanFactory, AbstractApplicationContext applicationContext) {
		// 拿到所有的BeanPostProcessor名称
		String[] postProcessorNames = beanFactory.getBeanNamesForType(BeanPostProcessor.class, true, false);
		
		// 优先注册一个BeanPostProcessorChecker的实例,这玩意是用作在BeanPostProcessor初始化的这个过程
		// 做检查用的，检查内容见扩展篇
		int beanProcessorTargetCount = beanFactory.getBeanPostProcessorCount() + 1 + postProcessorNames.length;
		beanFactory.addBeanPostProcessor(new BeanPostProcessorChecker(beanFactory, beanProcessorTargetCount));

		// 还是熟悉的套路，将三种优先级别的BeanPostProcessor分开
		List<BeanPostProcessor> priorityOrderedPostProcessors = new ArrayList<BeanPostProcessor>();
		List<BeanPostProcessor> internalPostProcessors = new ArrayList<BeanPostProcessor>();
		List<String> orderedPostProcessorNames = new ArrayList<String>();
		List<String> nonOrderedPostProcessorNames = new ArrayList<String>();
		for (String ppName : postProcessorNames) {
			// 先实例化实现了PriorityOrdered的，并加入集合
			if (beanFactory.isTypeMatch(ppName, PriorityOrdered.class)) {
				BeanPostProcessor pp = beanFactory.getBean(ppName, BeanPostProcessor.class);
				priorityOrderedPostProcessors.add(pp);
				// 如果是MergedBeanDefinitionPostProcessor，则加入internalPostProcessors
				if (pp instanceof MergedBeanDefinitionPostProcessor) {
					internalPostProcessors.add(pp);
				}
			} else if (beanFactory.isTypeMatch(ppName, Ordered.class)) {
				orderedPostProcessorNames.add(ppName);
			} else {
				nonOrderedPostProcessorNames.add(ppName);
			}
		}

		// 排序并注册
		OrderComparator.sort(priorityOrderedPostProcessors);
		registerBeanPostProcessors(beanFactory, priorityOrderedPostProcessors);

		// 接着注册实现了Ordered的
		List<BeanPostProcessor> orderedPostProcessors = new ArrayList<BeanPostProcessor>();
		for (String ppName : orderedPostProcessorNames) {
			BeanPostProcessor pp = beanFactory.getBean(ppName, BeanPostProcessor.class);
			orderedPostProcessors.add(pp);
			if (pp instanceof MergedBeanDefinitionPostProcessor) {
				internalPostProcessors.add(pp);
			}
		}
		OrderComparator.sort(orderedPostProcessors);
		registerBeanPostProcessors(beanFactory, orderedPostProcessors);

		// 再注册普通的
		List<BeanPostProcessor> nonOrderedPostProcessors = new ArrayList<BeanPostProcessor>();
		for (String ppName : nonOrderedPostProcessorNames) {
			BeanPostProcessor pp = beanFactory.getBean(ppName, BeanPostProcessor.class);
			nonOrderedPostProcessors.add(pp);
			if (pp instanceof MergedBeanDefinitionPostProcessor) {
				internalPostProcessors.add(pp);
			}
		}
		registerBeanPostProcessors(beanFactory, nonOrderedPostProcessors);

		// 重新注册所有的实现了MergedBeanDefinitionPostProcessor的（为了移动到处理链路的最后一个）
		// 不明白什么是处理链路的先往下看，到后边自然会明白
		OrderComparator.sort(internalPostProcessors);
		registerBeanPostProcessors(beanFactory, internalPostProcessors);

		// 重新注册ApplicationListenerDetector（也是为了移动到处理链路的最后一个）
		beanFactory.addBeanPostProcessor(new ApplicationListenerDetector(applicationContext));
	}

	/**
	 * Invoke the given BeanDefinitionRegistryPostProcessor beans.
	 */
	private static void invokeBeanDefinitionRegistryPostProcessors(
			Collection<? extends BeanDefinitionRegistryPostProcessor> postProcessors, BeanDefinitionRegistry registry) {
		for (BeanDefinitionRegistryPostProcessor postProcessor : postProcessors) {
			postProcessor.postProcessBeanDefinitionRegistry(registry);
		}
	}

	/**
	 * Invoke the given BeanFactoryPostProcessor beans.
	 */
	private static void invokeBeanFactoryPostProcessors(
			Collection<? extends BeanFactoryPostProcessor> postProcessors, ConfigurableListableBeanFactory beanFactory) {
		for (BeanFactoryPostProcessor postProcessor : postProcessors) {
			postProcessor.postProcessBeanFactory(beanFactory);
		}
	}

	/**
	 * Register the given BeanPostProcessor beans.
	 */
	private static void registerBeanPostProcessors(
			ConfigurableListableBeanFactory beanFactory, List<BeanPostProcessor> postProcessors) {

		for (BeanPostProcessor postProcessor : postProcessors) {
			beanFactory.addBeanPostProcessor(postProcessor);
		}
	}


	/**
	 * BeanPostProcessor that logs an info message when a bean is created during
	 * BeanPostProcessor instantiation, i.e. when a bean is not eligible for
	 * getting processed by all BeanPostProcessors.
	 */
	private static class BeanPostProcessorChecker implements BeanPostProcessor {

		private static final Log logger = LogFactory.getLog(BeanPostProcessorChecker.class);

		private final ConfigurableListableBeanFactory beanFactory;

		private final int beanPostProcessorTargetCount;

		public BeanPostProcessorChecker(ConfigurableListableBeanFactory beanFactory, int beanPostProcessorTargetCount) {
			this.beanFactory = beanFactory;
			this.beanPostProcessorTargetCount = beanPostProcessorTargetCount;
		}

		@Override
		public Object postProcessBeforeInitialization(Object bean, String beanName) {
			return bean;
		}

		@Override
		public Object postProcessAfterInitialization(Object bean, String beanName) {
			if (bean != null && !(bean instanceof BeanPostProcessor) &&
					this.beanFactory.getBeanPostProcessorCount() < this.beanPostProcessorTargetCount) {
				if (logger.isInfoEnabled()) {
					logger.info("Bean '" + beanName + "' of type [" + bean.getClass() +
							"] is not eligible for getting processed by all BeanPostProcessors " +
							"(for example: not eligible for auto-proxying)");
				}
			}
			return bean;
		}
	}


	/**
	 * BeanPostProcessor that detects beans which implement the ApplicationListener interface.
	 * This catches beans that can't reliably be detected by getBeanNamesForType.
	 */
	private static class ApplicationListenerDetector implements MergedBeanDefinitionPostProcessor, DestructionAwareBeanPostProcessor {

		private static final Log logger = LogFactory.getLog(ApplicationListenerDetector.class);

		private final AbstractApplicationContext applicationContext;

		private final Map<String, Boolean> singletonNames = new ConcurrentHashMap<String, Boolean>(64);

		public ApplicationListenerDetector(AbstractApplicationContext applicationContext) {
			this.applicationContext = applicationContext;
		}

		@Override
		public void postProcessMergedBeanDefinition(RootBeanDefinition beanDefinition, Class<?> beanType, String beanName) {
			if (beanDefinition.isSingleton()) {
				this.singletonNames.put(beanName, Boolean.TRUE);
			}
		}

		@Override
		public Object postProcessBeforeInitialization(Object bean, String beanName) {
			return bean;
		}

		@Override
		public Object postProcessAfterInitialization(Object bean, String beanName) {
			if (bean instanceof ApplicationListener) {
				// potentially not detected as a listener by getBeanNamesForType retrieval
				Boolean flag = this.singletonNames.get(beanName);
				if (Boolean.TRUE.equals(flag)) {
					// singleton bean (top-level or inner): register on the fly
					this.applicationContext.addApplicationListener((ApplicationListener<?>) bean);
				} else if (flag == null) {
					if (logger.isWarnEnabled() && !this.applicationContext.containsBean(beanName)) {
						// inner bean with other scope - can't reliably process events
						logger.warn("Inner bean '" + beanName + "' implements ApplicationListener interface " +
								"but is not reachable for event multicasting by its containing ApplicationContext " +
								"because it does not have singleton scope. Only top-level listener beans are allowed " +
								"to be of non-singleton scope.");
					}
					this.singletonNames.put(beanName, Boolean.FALSE);
				}
			}
			return bean;
		}

		@Override
		public void postProcessBeforeDestruction(Object bean, String beanName) {
			if (bean instanceof ApplicationListener) {
				ApplicationEventMulticaster multicaster = this.applicationContext.getApplicationEventMulticaster();
				multicaster.removeApplicationListener((ApplicationListener<?>) bean);
				multicaster.removeApplicationListenerBean(beanName);
			}
		}
	}

}
