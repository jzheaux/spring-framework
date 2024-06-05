/*
 * Copyright 2002-2024 the original author or authors.
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

package org.springframework.test.web.servlet.setup;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import jakarta.servlet.MultipartConfigElement;
import jakarta.servlet.Servlet;
import jakarta.servlet.ServletRegistration;
import jakarta.servlet.ServletSecurityElement;
import jakarta.servlet.http.HttpServletMapping;
import org.junit.jupiter.api.Test;

import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.DispatcherServlet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Tests for {@link MockMvcBuilders}
 *
 * @author Josh Cummings
 */
public class MockMvcBuildersTests {
	@Test
	public void httpServletMappingForDefault() throws Exception {
		MockServletContext servletContext = new MockServletContext();
		servletContext.addServlet("dispatcherServlet", DispatcherServlet.class).addMapping("/");
		servletContext.addServlet("testServlet", Servlet.class).addMapping("/test/*");
		StubWebApplicationContext root = new StubWebApplicationContext(servletContext);
		MockMvc mvc = MockMvcBuilders.webAppContextSetup(root).build();
		MockHttpServletRequest request = mvc.perform(get("/")).andReturn().getRequest();
		HttpServletMapping mapping = request.getHttpServletMapping();
		assertThat(mapping.getServletName()).isEqualTo("dispatcherServlet");
	}

	private static class MockServletContext extends org.springframework.mock.web.MockServletContext {
		private final Map<String, ServletRegistration> registrations = new HashMap<>();

		@Override
		public ServletRegistration.Dynamic addServlet(String servletName, Class<? extends Servlet> servletClass) {
			MockServletRegistration registration = new MockServletRegistration(servletName, servletClass);
			this.registrations.put(servletName, registration);
			return registration;
		}

		@Override
		public Map<String, ? extends ServletRegistration> getServletRegistrations() {
			return this.registrations;
		}
	}

	private static class MockServletRegistration implements ServletRegistration.Dynamic {

		private final String name;

		private final String className;

		private final Set<String> mappings = new LinkedHashSet<>();

		private MockServletRegistration(String name, Class<?> servlet) {
			this.name = name;
			this.className = servlet.getName();
		}

		@Override
		public Set<String> addMapping(String... urlPatterns) {
			mappings.addAll(Arrays.asList(urlPatterns));
			return mappings;
		}

		@Override
		public Collection<String> getMappings() {
			return mappings;
		}

		@Override
		public String getName() {
			return name;
		}

		@Override
		public String getClassName() {
			return className;
		}

		// not implemented

		@Override
		public void setLoadOnStartup(int loadOnStartup) {
			throw new UnsupportedOperationException();
		}

		@Override
		public void setRunAsRole(String roleName) {
			throw new UnsupportedOperationException();
		}

		@Override
		public String getRunAsRole() {
			throw new UnsupportedOperationException();
		}

		@Override
		public void setAsyncSupported(boolean isAsyncSupported) {
			throw new UnsupportedOperationException();
		}

		@Override
		public void setMultipartConfig(MultipartConfigElement multipartConfig) {
			throw new UnsupportedOperationException();
		}

		@Override
		public Set<String> setServletSecurity(ServletSecurityElement constraint) {
			throw new UnsupportedOperationException();
		}

		@Override
		public boolean setInitParameter(String name, String value) {
			throw new UnsupportedOperationException();
		}

		@Override
		public String getInitParameter(String name) {
			throw new UnsupportedOperationException();
		}

		@Override
		public Set<String> setInitParameters(Map<String, String> initParameters) {
			throw new UnsupportedOperationException();
		}

		@Override
		public Map<String, String> getInitParameters() {
			throw new UnsupportedOperationException();
		}
	}

}
