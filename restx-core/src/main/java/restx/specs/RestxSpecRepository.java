package restx.specs;

import com.google.common.base.Optional;
import com.google.common.collect.*;
import org.reflections.scanners.ResourcesScanner;
import org.reflections.util.ClasspathHelper;
import org.reflections.util.ConfigurationBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import restx.*;
import restx.factory.Component;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * User: xavierhanin
 * Date: 4/8/13
 * Time: 12:40 PM
 */
@Component
public class RestxSpecRepository {
    private final Logger logger = LoggerFactory.getLogger(RestxSpecRepository.class);

    private ImmutableMap<String, RestxSpec> allSpecs;
    private RestxSpecLoader specLoader;

    public RestxSpecRepository() {
        this(new RestxSpecLoader());
    }

    public RestxSpecRepository(RestxSpecLoader specLoader) {
        this.specLoader = specLoader;
    }


    public Iterable<String> findAll() {
        return findAllSpecs().keySet();
    }

    public Optional<RestxSpec> findSpecById(String id) {
        return Optional.fromNullable(findAllSpecs().get(id));
    }

    public Iterable<String> findSpecsByOperation(String httpMethod, String path) {
        return filterSpecsByOperation(findAllSpecs(), httpMethod, path);
    }

    public Iterable<RestxSpec.WhenHttpRequest> findSpecsByRequest(RestxRequest request) {
        return findWhensMatchingRequest(findAllSpecs(), request);
    }

    synchronized ImmutableMap<String, RestxSpec> findAllSpecs() {
        if (allSpecs == null) {
            Map<String, RestxSpec> specsMap = Maps.newLinkedHashMap();
            Set<String> specs = new ConfigurationBuilder()
                    .setUrls(ClasspathHelper.forPackage(""))
                    .setScanners(new ResourcesScanner())
                    .build()
                    .getResources(Pattern.compile(".*\\.spec\\.yaml"));
            for (String spec : specs) {
                try {
                    specsMap.put(spec, specLoader.load(spec));
                } catch (IOException e) {
                    logger.warn("io exception while loading restx spec " + spec + ": " + e, e);
                }
            }
            allSpecs = ImmutableMap.copyOf(specsMap);
        }
        return allSpecs;
    }

    Iterable<String> filterSpecsByOperation(ImmutableMap<String, RestxSpec> allSpecs,
                                                    String httpMethod, String path) {
        StdRouteMatcher matcher = new StdRouteMatcher(httpMethod, path);
        Collection<String> specs = Lists.newArrayList();
        for (Map.Entry<String, RestxSpec> spec : allSpecs.entrySet()) {
            for (RestxSpec.When when : spec.getValue().getWhens()) {
                if (when instanceof RestxSpec.WhenHttpRequest) {
                    RestxSpec.WhenHttpRequest request = (RestxSpec.WhenHttpRequest) when;
                    String requestPath = request.getPath();
                    if (!requestPath.startsWith("/")) {
                        requestPath = "/" + requestPath;
                    }
                    if (requestPath.indexOf("?") != -1) {
                        requestPath = requestPath.substring(0, requestPath.indexOf("?"));
                    }
                    Optional<RestxRouteMatch> match = matcher.match(HANDLER, request.getMethod(), requestPath);
                    if (match.isPresent()) {
                        specs.add(spec.getKey());
                        break;
                    }
                }
            }
        }
        return specs;
    }

    Iterable<RestxSpec.WhenHttpRequest> findWhensMatchingRequest(ImmutableMap<String, RestxSpec> allSpecs, RestxRequest restxRequest) {
        Collection<RestxSpec.WhenHttpRequest> matchingRequestsSpecs = Lists.newArrayList();
        for (Map.Entry<String, RestxSpec> spec : allSpecs.entrySet()) {
            for (RestxSpec.When when : spec.getValue().getWhens()) {
                if (when instanceof RestxSpec.WhenHttpRequest) {
                    RestxSpec.WhenHttpRequest request = (RestxSpec.WhenHttpRequest) when;
                    String requestPath = request.getPath();
                    if (!requestPath.startsWith("/")) {
                        requestPath = "/" + requestPath;
                    }
                    StdRequest stdRequest = StdRequest.builder()
                            .setBaseUri("http://restx.io") // baseUri is required but we won't use it
                            .setHttpMethod(request.getMethod()).setFullPath(requestPath).build();

                    if (restxRequest.getHttpMethod().equals(stdRequest.getHttpMethod())
                            && restxRequest.getRestxPath().equals(stdRequest.getRestxPath())) {
                        MapDifference<String, ImmutableList<String>> difference =
                                Maps.difference(restxRequest.getQueryParams(), stdRequest.getQueryParams());
                        if (difference.entriesOnlyOnRight().isEmpty()
                                && difference.entriesDiffering().isEmpty()) {
                            matchingRequestsSpecs.add(request);
                            break;
                        }
                    }
                }
            }
        }
        return matchingRequestsSpecs;
    }

    private static final RestxHandler HANDLER = new RestxHandler() {
        @Override
        public Optional<RestxRouteMatch> match(RestxRequest req) {
            return Optional.absent();
        }

        @Override
        public void handle(RestxRouteMatch match, RestxRequest req, RestxResponse resp, RestxContext ctx) throws IOException {
        }
    };


}
