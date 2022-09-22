package endpoints.task;

import com.databasesandlife.util.DomParser;
import com.databasesandlife.util.ThreadPool.SynchronizationPoint;
import com.databasesandlife.util.gwtsafe.ConfigurationException;
import com.jayway.jsonpath.InvalidJsonException;
import com.jayway.jsonpath.PathNotFoundException;
import com.offerready.xslt.WeaklyCachedXsltTransformer.XsltCompilationThreads;
import endpoints.HttpRequestSpecification;
import endpoints.HttpRequestSpecification.HttpRequestFailedException;
import endpoints.TransformationContext;
import endpoints.config.IntermediateValueName;
import endpoints.config.ParameterName;
import endpoints.config.Transformer;
import endpoints.task.HttpOutputIntermediateValue.HttpJsonOutputIntermediateValue;
import endpoints.task.HttpOutputIntermediateValue.HttpXPathOutputIntermediateValue;
import org.w3c.dom.Element;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.xml.xpath.XPathExpressionException;
import java.io.File;
import java.io.IOException;
import java.net.URLConnection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static javax.xml.xpath.XPathConstants.STRING;

/** Performs an HTTP request. See "configuration.md" for more information */
public class HttpRequestTask extends Task {
    
    protected @Nonnull HttpRequestSpecification spec;
    protected @Nonnull List<HttpOutputIntermediateValue> outputIntermediateValues;

    public HttpRequestTask(
        @Nonnull XsltCompilationThreads threads, @Nonnull File httpXsltDirectory, @Nonnull File ooxmlDir, 
        @Nonnull Map<String, Transformer> transformers, @Nonnull File staticDir,
        int indexFromZero, @Nonnull Element config
    ) throws ConfigurationException {
        super(threads, httpXsltDirectory, ooxmlDir, transformers, staticDir, indexFromZero, config);
        spec = new HttpRequestSpecification(threads, httpXsltDirectory, config);
        outputIntermediateValues = HttpOutputIntermediateValue.parse(
            DomParser.getSubElements(config, "output-intermediate-value"));
        if (spec.ignoreIfError && ! outputIntermediateValues.isEmpty())
            throw new ConfigurationException("HTTP task cannot ignore-if-error='true' and output intermediate values");
    }

    @Override
    public void assertParametersSuffice(@Nonnull Set<ParameterName> params) throws ConfigurationException {
        super.assertParametersSuffice(params);
        spec.assertParametersSuffice(params, inputIntermediateValues);
    }
    
    @Override public @Nonnull Set<IntermediateValueName> getOutputIntermediateValues() {
        return outputIntermediateValues.stream().map(v -> v.name).collect(Collectors.toSet());
    }
    
    protected void parseResults(
        @Nonnull Map<IntermediateValueName, String> outputValues, 
        @CheckForNull URLConnection urlConnection
    ) {
        // No need to capture output values, so don't even parse the response (or wait for it)
        if (outputIntermediateValues.isEmpty()) return;

        // If this request is set to "ignore errors" and an error occurred, ignore it
        if (urlConnection == null) return;

        var url = urlConnection.getURL();
        try {
            if (urlConnection.getContentType().toLowerCase().contains("xml")) {
                try (var inputStream = urlConnection.getInputStream()) {
                    var downloadedXml = DomParser.from(inputStream);
                    for (var x : outputIntermediateValues) {
                        if ( ! (x instanceof HttpXPathOutputIntermediateValue))
                            throw new HttpRequestFailedException(url.toExternalForm(), null,
                                "URL '" + url + "' returned XML, yet output variables request JSONPath");
                        // value can be empty string if it doesn't match :-(
                        var value = (String) ((HttpXPathOutputIntermediateValue) x).xpath.evaluate(downloadedXml, STRING);
                        if (x.regex != null && ! x.regex.matcher(value).matches())
                            throw new HttpRequestFailedException(url.toExternalForm(), null, "URL '" + url + "': " +
                                "XPath returned '"+value+"' which does not match regex '" + x.regex.pattern() + "'");
                        outputValues.put(x.name, value);
                    }
                }
                catch (ConfigurationException e) {
                    throw new HttpRequestFailedException(url.toExternalForm(), null,
                        "Cannot parse XML response from '" + url + "'", e);
                }
            }
            else if (urlConnection.getContentType().toLowerCase().contains("json")) {
                try (var inputStream = urlConnection.getInputStream()) {
                    for (var x : outputIntermediateValues) {
                        if ( ! (x instanceof HttpJsonOutputIntermediateValue i))
                            throw new HttpRequestFailedException(url.toExternalForm(), null,
                                "URL '" + url + "' returned JSON, yet output variables request XPath");
                        // JsonSmartJsonProvider, net.minidev.json.parser
                        var value = i.jsonPath.read(inputStream);
                        if (value instanceof List)
                            throw new HttpRequestFailedException(url.toExternalForm(), null, "URL '" + url + "': " +
                                "JSONPath returned an array, whereas a string or number is required");
                        if (value instanceof Map)
                            throw new HttpRequestFailedException(url.toExternalForm(), null, "URL '" + url + "': " +
                                "JSONPath returned a JSON object node (= key-value map), whereas a string or number is required");
                        var stringValue = value.toString(); // Discovered at least: String, Integer, Double, Long
                        if (x.regex != null && ! x.regex.matcher(stringValue).matches())
                            throw new HttpRequestFailedException(url.toExternalForm(), null, "URL '" + url + "': " +
                                "JSONPath returned '"+stringValue+"' which does not match regex '" + x.regex.pattern() + "'");
                        outputValues.put(x.name, stringValue);
                    }
                }
                catch (InvalidJsonException e) {
                    throw new HttpRequestFailedException(url.toExternalForm(), null,
                        "Cannot parse JSON response from '" + url + "'", e);
                }
                catch (PathNotFoundException e) {
                    throw new HttpRequestFailedException(url.toExternalForm(), null,
                        "JSONPath not found in response from '" + url + "'", e);
                }
            }
            else throw new HttpRequestFailedException(url.toExternalForm(), null,
                    "URL '" + url + "' returned an unexpected content type " +
                        "'" + urlConnection.getContentType() + "': Expecting XML or JSON");
        }
        catch (IOException | XPathExpressionException | HttpRequestFailedException e) {
            spec.throwException(url.toExternalForm(), e);
        }
    }
    
    @Override
    protected void executeThenScheduleSynchronizationPoint(
        @Nonnull TransformationContext context,
        @Nonnull SynchronizationPoint workComplete
    ) {
        spec.scheduleExecutionAndAssertNoError(context, inputIntermediateValues, (@CheckForNull var urlConnection) -> {
            parseResults(context.intermediateValues, urlConnection);
            context.threads.addTask(workComplete);
        });
    }
}
