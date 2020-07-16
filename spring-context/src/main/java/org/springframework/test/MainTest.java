/*
 * Copyright 2002-2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.test;

import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * 
 * @author admin
 */
public class MainTest {
	public static void main(String[] args) {
//		ApplicationContext acac = new AnnotationConfigApplicationContext(Config.class);
//		Person aPerson = acac.getBean(Person.class);
//		System.out.println(aPerson.getClass().getName());
		
		ApplicationContext applicationContext =
                new ClassPathXmlApplicationContext("classpath*:application-context.xml");
        Person aTest = (Person)applicationContext.getBean("aPerson");
        aTest.doSomething();
	}

}