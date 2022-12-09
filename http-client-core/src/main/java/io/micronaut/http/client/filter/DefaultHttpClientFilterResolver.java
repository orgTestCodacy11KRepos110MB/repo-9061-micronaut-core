/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.http.client.filter;

import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationMetadataResolver;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.annotation.Filter;
import io.micronaut.http.annotation.FilterMatcher;
import io.micronaut.http.filter.FilterPatternStyle;
import io.micronaut.http.filter.GenericHttpFilter;
import io.micronaut.http.filter.HttpClientFilter;
import io.micronaut.http.filter.HttpClientFilterResolver;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Resolves filters for http clients.
 *
 * @author James Kleeh
 * @author graemerocher
 * @since 1.3.0
 */
@Internal
@Singleton
@BootstrapContextCompatible
public class DefaultHttpClientFilterResolver implements HttpClientFilterResolver<ClientFilterResolutionContext> {

    private final List<HttpClientFilter> clientFilters;
    private final AnnotationMetadataResolver annotationMetadataResolver;

    /**
     * Default constructor.
     *
     * @param annotationMetadataResolver The annotation metadata resolver
     * @param clientFilters              All client filters
     */
    public DefaultHttpClientFilterResolver(
            AnnotationMetadataResolver annotationMetadataResolver,
            List<HttpClientFilter> clientFilters) {
        this.annotationMetadataResolver = annotationMetadataResolver;
        this.clientFilters = clientFilters;
    }

    @Override
    public List<FilterEntry> resolveFilterEntries(ClientFilterResolutionContext context) {
        return clientFilters.stream()
                .map(httpClientFilter -> {
                    AnnotationMetadata annotationMetadata = annotationMetadataResolver.resolveMetadata(httpClientFilter);
                    HttpMethod[] methods = annotationMetadata.enumValues(Filter.class, "methods", HttpMethod.class);
                    FilterPatternStyle patternStyle = annotationMetadata.enumValue(Filter.class,
                        "patternStyle", FilterPatternStyle.class).orElse(FilterPatternStyle.ANT);
                    final Set<HttpMethod> httpMethods = new HashSet<>(Arrays.asList(methods));
                    if (annotationMetadata.hasStereotype(FilterMatcher.class)) {
                        httpMethods.addAll(
                                Arrays.asList(annotationMetadata.enumValues(FilterMatcher.class, "methods", HttpMethod.class))
                        );
                    }

                    return FilterEntry.of(
                            httpClientFilter,
                            annotationMetadata,
                            httpMethods,
                            patternStyle,
                            annotationMetadata.stringValues(Filter.class)
                    );
                }).filter(entry -> {
                    AnnotationMetadata annotationMetadata = entry.getAnnotationMetadata();
                    boolean matches = !annotationMetadata.hasStereotype(FilterMatcher.class);
                    String filterAnnotation = annotationMetadata.getAnnotationNameByStereotype(FilterMatcher.class).orElse(null);
                    if (filterAnnotation != null && !matches) {
                        matches = context.getAnnotationMetadata().hasStereotype(filterAnnotation);
                    }

                    if (matches) {
                        String[] serviceIds = annotationMetadata.stringValues(Filter.class, "serviceId");
                        if (ArrayUtils.isNotEmpty(serviceIds)) {
                            matches = containsIdentifier(context.getClientIds(), serviceIds);
                        }
                    }
                    if (matches) {
                        String[] serviceIdsExclude = annotationMetadata.stringValues(Filter.class, "excludeServiceId");
                        if (ArrayUtils.isNotEmpty(serviceIdsExclude)) {
                            matches = !containsIdentifier(context.getClientIds(), serviceIdsExclude);
                        }
                    }
                    return matches;
                }).collect(Collectors.toList());
    }

    @Override
    public List<GenericHttpFilter> resolveFilters(HttpRequest<?> request, List<FilterEntry> filterEntries) {
        String requestPath = StringUtils.prependUri("/", request.getUri().getPath());
        io.micronaut.http.HttpMethod method = request.getMethod();
        List<GenericHttpFilter> filterList = new ArrayList<>(filterEntries.size());
        for (FilterEntry filterEntry : filterEntries) {
            final GenericHttpFilter filter = filterEntry.getFilter();
            if (filter instanceof GenericHttpFilter.AroundLegacy al && !al.isEnabled()) {
                continue;
            }
            boolean matches = true;
            if (filterEntry.hasMethods()) {
                matches = anyMethodMatches(method, filterEntry.getFilterMethods());
            }
            if (filterEntry.hasPatterns()) {
                matches = matches && anyPatternMatches(requestPath, filterEntry.getPatterns(), filterEntry.getPatternStyle());
            }

            if (matches) {
                filterList.add(filter);
            }
        }
        return filterList;
    }

    private boolean containsIdentifier(Collection<String> clientIdentifiers, String[] clients) {
        return Arrays.stream(clients).anyMatch(clientIdentifiers::contains);
    }

    private boolean anyPatternMatches(String requestPath, String[] patterns, FilterPatternStyle patternStyle) {
        return Arrays.stream(patterns).anyMatch(pattern -> patternStyle.getPathMatcher().matches(pattern, requestPath));
    }

    private boolean anyMethodMatches(HttpMethod requestMethod, Collection<HttpMethod> methods) {
        return methods.contains(requestMethod);
    }

}
