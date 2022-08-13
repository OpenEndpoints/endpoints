package endpoints.task;

import com.databasesandlife.util.DomParser;
import com.databasesandlife.util.gwtsafe.ConfigurationException;
import com.jayway.jsonpath.InvalidPathException;
import com.jayway.jsonpath.JsonPath;
import endpoints.config.IntermediateValueName;
import org.w3c.dom.Element;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public abstract class HttpOutputIntermediateValue {
    
    public @Nonnull IntermediateValueName name;
    public @CheckForNull Pattern regex;

    public static class HttpXPathOutputIntermediateValue extends HttpOutputIntermediateValue {
        public final @Nonnull XPathExpression xpath;
        public HttpXPathOutputIntermediateValue(@Nonnull String xpath) throws ConfigurationException {
            try { this.xpath = DomParser.getExpression(xpath); }
            catch (XPathExpressionException e) { throw new ConfigurationException(e); }
        }
    }

    public static class HttpJsonOutputIntermediateValue extends HttpOutputIntermediateValue {
        public final @Nonnull JsonPath jsonPath;
        public HttpJsonOutputIntermediateValue(@Nonnull String jsonPathStr) throws ConfigurationException {
            try { jsonPath = JsonPath.compile(jsonPathStr); }
            catch (InvalidPathException e) { throw new ConfigurationException(e); }
        }
    }

    public static List<HttpOutputIntermediateValue> parse(List<Element> configElements) throws ConfigurationException {
        var result = new ArrayList<HttpOutputIntermediateValue>();
        for (var config : configElements) {
            var name = new IntermediateValueName(DomParser.getMandatoryAttribute(config, "name"));
            try {
                HttpOutputIntermediateValue var;
                var xpath = DomParser.getOptionalAttribute(config, "xpath");
                var jsonpath = DomParser.getOptionalAttribute(config, "jsonpath");
                if (xpath == null && jsonpath == null) 
                    throw new ConfigurationException("Either 'xpath' or 'jsonpath' attributes must be set");
                if (xpath != null && jsonpath != null) 
                    throw new ConfigurationException("Only one of 'xpath' or 'jsonpath' attributes may be set");
                if (xpath != null) var = new HttpXPathOutputIntermediateValue(xpath);
                else var = new HttpJsonOutputIntermediateValue(jsonpath);
                 
                var.name = name;
                var.regex = Optional.ofNullable(DomParser.getOptionalAttribute(config, "regex"))
                    .map(src -> Pattern.compile(src)).orElse(null);
                
                result.add(var);
            }
            catch (ConfigurationException | PatternSyntaxException e) {
                throw new ConfigurationException("<"+config.getNodeName()+" name='" + name.name + "'>", e);
            }
        }
        return result;
    }
}
