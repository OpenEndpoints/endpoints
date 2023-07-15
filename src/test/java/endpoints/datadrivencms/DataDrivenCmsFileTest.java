package endpoints.datadrivencms;

import com.databasesandlife.util.DomParser;
import endpoints.LazyCachingValue;
import junit.framework.TestCase;

import java.util.List;
import java.util.Map;

public class DataDrivenCmsFileTest extends TestCase {

    public void testCreateDataSourceOutput() throws Exception {
        var globalConditionDoesntMatch = """
            <data-driven-cms>
                <condition if="${trueParameter}" equals="false"/>   <!-- never matches -->
                <content id="main">
                    <instance priority="5">
                        <copy><element>global-condition-doesnt-match</element></copy>
                    </instance>
                </content>
                <property id="main">
                    <instance priority="5" value="global-condition-doesnt-match"/>
                </property>
            </data-driven-cms>
            """;
        var mainFile = """
            <data-driven-cms>
                <content id="main">
                    <instance priority="5">
                        <condition-folder>
                            <condition if="${trueParameter}" equals="false"/>   <!-- never matches -->
                        </condition-folder>
                        <copy><element>local-condition-doesnt-match</element></copy>
                    </instance>
                </content>
                <property id="main">
                    <instance priority="5" value="local-condition-doesnt-match">
                        <condition-folder>
                            <condition if="${trueParameter}" equals="false"/>   <!-- never matches -->
                        </condition-folder>
                    </instance>
                </property>
                
                <content id="main">
                    <instance priority="4">
                        <copy><element>prio4</element></copy>
                    </instance>
                </content>
                <property id="main">
                    <instance priority="4" value="prio4"/>
                </property>
                
                <content id="main">
                    <instance priority="3">
                        <copy><element>prio3</element></copy>
                    </instance>
                </content>
                <property id="main">
                    <instance priority="3" value="prio3"/>
                </property>
                
                <content id="other">
                    <instance priority="6">
                        <copy><element>other</element></copy>
                    </instance>
                </content>
                <property id="other">
                    <instance priority="6" value="other"/>
                </property>
                
            </data-driven-cms>
            """;
        var otherFileThatGetsMerged = """
            <data-driven-cms>
                <content id="main">
                    <instance priority="2">  <!-- lower priority, so is lower down in output -->
                        <copy><element>prio2</element></copy>
                    </instance>
                </content>
                <property id="main">  <!-- lower priority, so is ignored -->
                    <instance priority="2" value="prio2"/>
                </property>
            </data-driven-cms>
            """;
        
        var files = List.of(
            new DataDrivenCmsFile(DomParser.from(globalConditionDoesntMatch)),
            new DataDrivenCmsFile(DomParser.from(mainFile)),
            new DataDrivenCmsFile(DomParser.from(otherFileThatGetsMerged))
        );

        var params = Map.of("trueParameter", LazyCachingValue.newFixed("true"));
        var output = DataDrivenCmsFile.createDataSourceOutput("||", params, files);
        var stringOutput = DomParser.formatXmlPretty(output);
        var expected = """
            <data-driven-cms>
               <content id="main">
                  <instance>
                     <element>prio4</element>
                  </instance>
                  <instance>
                     <element>prio3</element>
                  </instance>
                  <instance>
                     <element>prio2</element>
                  </instance>
               </content>
               <content id="other">
                  <instance>
                     <element>other</element>
                  </instance>
               </content>
               <property id="main" value="prio4"/>
               <property id="other" value="other"/>
            </data-driven-cms>""";
        
        assertEquals(expected, stringOutput);
    }
}
