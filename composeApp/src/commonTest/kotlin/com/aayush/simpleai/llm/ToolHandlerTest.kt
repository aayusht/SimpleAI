package com.aayush.simpleai.llm

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ToolHandlerTest {

    @Test
    fun testParseDuckDuckGoLiteLinks() {

        val links = parseDuckDuckGoLiteLinks(TEST_DDG_HTML)

        assertEquals(10, links.size)
        assertEquals("10 Day Weather - Downtown, San Francisco, California", links[0].first)
        assertEquals("https://weather.com/weather/tenday/l/San%20Francisco%20CA%20USCA0987:1:US", links[0].second)
    }

    @Test
    fun testExtractMainText() {
        val extracted = extractMainText(TEST_DDG_HTML)
        
        // Should contain the weather descriptions from the snippets
        assertTrue(extracted.contains("Be prepared with the most accurate 10-day forecast"))
        assertTrue(extracted.contains("San Francisco, CA Weather Forecast, with current conditions"))
    }
}

private const val TEST_DDG_HTML = """
    <!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" "http://www.w3.org/TR/html4/loose.dtd">
<html>

<head>
  <meta http-equiv="content-type" content="text/html; charset=UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=3.0, user-scalable=1;">
  <meta name="referrer" content="origin">
  <meta name="HandheldFriendly" content="true" />
  <meta name="robots" content="noindex, nofollow" />
  <title>weather san francisco at DuckDuckGo</title>
  <link title="DuckDuckGo (Lite)" type="application/opensearchdescription+xml" rel="search" href="//duckduckgo.com/opensearch_lite_v2.xml">
  <link href="//duckduckgo.com/favicon.ico" rel="shortcut icon" />
  <link rel="icon" href="//duckduckgo.com/favicon.ico" type="image/x-icon" />
  <link id="icon60" rel="apple-touch-icon" href="//duckduckgo.com/assets/icons/meta/DDG-iOS-icon_60x60.png?v=2"/>
  <link id="icon76" rel="apple-touch-icon" sizes="76x76" href="//duckduckgo.com/assets/icons/meta/DDG-iOS-icon_76x76.png?v=2"/>
  <link id="icon120" rel="apple-touch-icon" sizes="120x120" href="//duckduckgo.com/assets/icons/meta/DDG-iOS-icon_120x120.png?v=2"/>
  <link id="icon152" rel="apple-touch-icon" sizes="152x152" href="//duckduckgo.com/assets/icons/meta/DDG-iOS-icon_152x152.png?v=2"/>
  <link rel="image_src" href="//duckduckgo.com/assets/icons/meta/DDG-icon_256x256.png">
  <link rel="stylesheet" media="handheld, all" href="//duckduckgo.com/dist/lr.ee29b42e9c3cc71a19de.css" type="text/css"/>
</head>

<body>
  <p class='extra'>&nbsp;</p>
  <div class="header">
    DuckDuckGo
  </div>
  <p class='extra'>&nbsp;</p>

  <form action="/lite/" method="post">
      <input class='query' type="text" size="40" name="q" value="weather san francisco">
      <input class='submit' type="submit" value="Search">
      
      
      
      

      <div class="filters">
        <select class="submit" name="kl">
          
            <option value="" >All Regions</option>
          
            <option value="ar-es" >Argentina</option>
          
            <option value="au-en" >Australia</option>
          
            <option value="at-de" >Austria</option>
          
            <option value="be-fr" >Belgium (fr)</option>
          
            <option value="be-nl" >Belgium (nl)</option>
          
            <option value="br-pt" >Brazil</option>
          
            <option value="bg-bg" >Bulgaria</option>
          
            <option value="ca-en" >Canada (en)</option>
          
            <option value="ca-fr" >Canada (fr)</option>
          
            <option value="ct-ca" >Catalonia</option>
          
            <option value="cl-es" >Chile</option>
          
            <option value="cn-zh" >China</option>
          
            <option value="co-es" >Colombia</option>
          
            <option value="hr-hr" >Croatia</option>
          
            <option value="cz-cs" >Czech Republic</option>
          
            <option value="dk-da" >Denmark</option>
          
            <option value="ee-et" >Estonia</option>
          
            <option value="fi-fi" >Finland</option>
          
            <option value="fr-fr" >France</option>
          
            <option value="de-de" >Germany</option>
          
            <option value="gr-el" >Greece</option>
          
            <option value="hk-tzh" >Hong Kong</option>
          
            <option value="hu-hu" >Hungary</option>
          
            <option value="is-is" >Iceland</option>
          
            <option value="in-en" >India (en)</option>
          
            <option value="id-en" >Indonesia (en)</option>
          
            <option value="ie-en" >Ireland</option>
          
            <option value="il-en" >Israel (en)</option>
          
            <option value="it-it" >Italy</option>
          
            <option value="jp-jp" >Japan</option>
          
            <option value="kr-kr" >Korea</option>
          
            <option value="lv-lv" >Latvia</option>
          
            <option value="lt-lt" >Lithuania</option>
          
            <option value="my-en" >Malaysia (en)</option>
          
            <option value="mx-es" >Mexico</option>
          
            <option value="nl-nl" >Netherlands</option>
          
            <option value="nz-en" >New Zealand</option>
          
            <option value="no-no" >Norway</option>
          
            <option value="pk-en" >Pakistan (en)</option>
          
            <option value="pe-es" >Peru</option>
          
            <option value="ph-en" >Philippines (en)</option>
          
            <option value="pl-pl" >Poland</option>
          
            <option value="pt-pt" >Portugal</option>
          
            <option value="ro-ro" >Romania</option>
          
            <option value="ru-ru" >Russia</option>
          
            <option value="xa-ar" >Saudi Arabia</option>
          
            <option value="sg-en" >Singapore</option>
          
            <option value="sk-sk" >Slovakia</option>
          
            <option value="sl-sl" >Slovenia</option>
          
            <option value="za-en" >South Africa</option>
          
            <option value="es-ca" >Spain (ca)</option>
          
            <option value="es-es" >Spain (es)</option>
          
            <option value="se-sv" >Sweden</option>
          
            <option value="ch-de" >Switzerland (de)</option>
          
            <option value="ch-fr" >Switzerland (fr)</option>
          
            <option value="tw-tzh" >Taiwan</option>
          
            <option value="th-en" >Thailand (en)</option>
          
            <option value="tr-tr" >Turkey</option>
          
            <option value="us-en" >US (English)</option>
          
            <option value="us-es" >US (Spanish)</option>
          
            <option value="ua-uk" >Ukraine</option>
          
            <option value="uk-en" >United Kingdom</option>
          
            <option value="vn-en" >Vietnam (en)</option>
          
        </select>

        <select class="submit" name="df">
          
            <option value="" selected>Any Time</option>
          
            <option value="d" >Past Day</option>
          
            <option value="w" >Past Week</option>
          
            <option value="m" >Past Month</option>
          
            <option value="y" >Past Year</option>
          
        </select>
      </div>
  </form>

  
    <p class="extra">&nbsp;</p>
    <table border="0">
      <tr>
        <td>
          
        </td>
        <td>
          
            <!-- Next Page Button Sub-template -->
<form class="next_form" action="/lite/" method="post">
    <input type="submit" class='navbutton' value="Next Page &gt;">
    <input type="hidden" name="q" value="weather san francisco">
    <input type="hidden" name="s" value="10">
    <input type="hidden" name="nextParams" value="">
    <input type="hidden" name="v" value="l">
    <input type="hidden" name="o" value="json">
    <input type="hidden" name="dc" value="11">
    <input type="hidden" name="api" value="d.js">
    <input type="hidden" name="vqd" value="4-191870771335507362608829693484983654615">
    &nbsp;&nbsp;&nbsp;&nbsp;
    
    
    
      <input name="kl" value="wt-wt" type="hidden">
    
    
    
    
</form>
          
        </td>
      </tr>
    </table>
  

  

  <p class='extra'>&nbsp;</p>

  <table border="0">
    
  </table>

  <table border="0">
    
      
      <!-- Web results are present -->
      
        
            <tr>
              
                <td valign="top">
                  
                    1.&nbsp;
                  
                </td>
                <td>
                  <a rel="nofollow" href="//duckduckgo.com/l/?uddg=https%3A%2F%2Fweather.com%2Fweather%2Ftenday%2Fl%2FSan%2520Francisco%2520CA%2520USCA0987%3A1%3AUS&amp;rut=72007b3d5e608ed4534a7ab42855b52cd97d5ae685f5742e37e9aff6d67524c5" class='result-link'>10 Day Weather - Downtown, San Francisco, California</a>
                </td>
                
            </tr>

            
              
                <!-- Only show abstract separately if there's a click URL (not EOF) -->
                <tr>
                  <td>&nbsp;&nbsp;&nbsp;</td>
                  <td class='result-snippet'>
                    Be prepared with the most accurate 10-day forecast for Downtown, <b>San</b> <b>Francisco</b>, California with highs, lows, chance of precipitation from The <b>Weather</b> Channel and <b>Weather</b>.com
                  </td>
                </tr>
              
            

            
              <tr>
                <td>&nbsp;&nbsp;&nbsp;</td>
                <td>
                  <span class='link-text'>weather.com/weather/tenday/l/San Francisco CA USCA0987:1:US</span>
                  
                </td>
              </tr>
            

            <tr>
              <td>&nbsp;</td>
              <td>&nbsp;</td>
            </tr>

        
      
        
            <tr>
              
                <td valign="top">
                  
                    2.&nbsp;
                  
                </td>
                <td>
                  <a rel="nofollow" href="//duckduckgo.com/l/?uddg=https%3A%2F%2Fwww.accuweather.com%2Fen%2Fus%2Fsan%2Dfrancisco%2F94103%2Fweather%2Dforecast%2F347629&amp;rut=29c2a0364b66d943a37ce3cc92f20b7d617f4007d16e497b5a55e5ea16f3eb8b" class='result-link'>San Francisco, CA Weather Forecast | AccuWeather</a>
                </td>
                
            </tr>

            
              
                <!-- Only show abstract separately if there's a click URL (not EOF) -->
                <tr>
                  <td>&nbsp;&nbsp;&nbsp;</td>
                  <td class='result-snippet'>
                    <b>San</b> <b>Francisco</b>, CA <b>Weather</b> Forecast, with current conditions, wind, air quality, and what to expect for the next 3 days.
                  </td>
                </tr>
              
            

            
              <tr>
                <td>&nbsp;&nbsp;&nbsp;</td>
                <td>
                  <span class='link-text'>www.accuweather.com/en/us/san-francisco/94103/weather-forecast/347629</span>
                  
                </td>
              </tr>
            

            <tr>
              <td>&nbsp;</td>
              <td>&nbsp;</td>
            </tr>

        
      
        
            <tr>
              
                <td valign="top">
                  
                    3.&nbsp;
                  
                </td>
                <td>
                  <a rel="nofollow" href="//duckduckgo.com/l/?uddg=https%3A%2F%2Fforecast.weather.gov%2Fzipcity.php%3Finputstring%3DSan%2BFrancisco%2CCA&amp;rut=4643978a98f205f7fc63c9fc76b0aa8edc2cede35372bacce4cae5fcee88c62e" class='result-link'>7-Day Forecast 37.77N 122.41W - National Weather Service</a>
                </td>
                
            </tr>

            
              
                <!-- Only show abstract separately if there's a click URL (not EOF) -->
                <tr>
                  <td>&nbsp;&nbsp;&nbsp;</td>
                  <td class='result-snippet'>
                    Your local forecast office is <b>San</b> <b>Francisco</b> Bay Area/Monterey, CA Lake-effect Snow and Whiteout Conditions in the Great Lakes Region; Below-average Temperatures in the East Heavy lake-effect and lake-enhanced snow will persist downwind of the Great Lakes and produce some whiteout conditions that could cause difficult travel conditions.
                  </td>
                </tr>
              
            

            
              <tr>
                <td>&nbsp;&nbsp;&nbsp;</td>
                <td>
                  <span class='link-text'>forecast.weather.gov/zipcity.php?inputstring=San+Francisco,CA</span>
                  
                    &nbsp;&nbsp;&nbsp;
                    <span class='timestamp'>2026-01-20T09:43:00.0000000</span>
                  
                </td>
              </tr>
            

            <tr>
              <td>&nbsp;</td>
              <td>&nbsp;</td>
            </tr>

        
      
        
            <tr>
              
                <td valign="top">
                  
                    4.&nbsp;
                  
                </td>
                <td>
                  <a rel="nofollow" href="//duckduckgo.com/l/?uddg=https%3A%2F%2Fwww.weather%2Dforecast.com%2Flocations%2FSan%2DFrancisco%2Fforecasts%2Flatest&amp;rut=1322c8545f8adb3c72e9957d162cae4bdc281e2e538b7d5555e931263a0258f1" class='result-link'>San Francisco Weather Forecast</a>
                </td>
                
            </tr>

            
              
                <!-- Only show abstract separately if there's a click URL (not EOF) -->
                <tr>
                  <td>&nbsp;&nbsp;&nbsp;</td>
                  <td class='result-snippet'>
                    <b>San</b> <b>Francisco</b> <b>Weather</b> Forecast. Providing a local hourly <b>San</b> <b>Francisco</b> <b>weather</b> forecast of rain, sun, wind, humidity and temperature. The Long-range 12 day forecast also includes detail for <b>San</b> <b>Francisco</b> <b>weather</b> today.
                  </td>
                </tr>
              
            

            
              <tr>
                <td>&nbsp;&nbsp;&nbsp;</td>
                <td>
                  <span class='link-text'>www.weather-forecast.com/locations/San-Francisco/forecasts/latest</span>
                  
                    &nbsp;&nbsp;&nbsp;
                    <span class='timestamp'>2026-01-20T00:00:00.0000000</span>
                  
                </td>
              </tr>
            

            <tr>
              <td>&nbsp;</td>
              <td>&nbsp;</td>
            </tr>

        
      
        
            <tr>
              
                <td valign="top">
                  
                    5.&nbsp;
                  
                </td>
                <td>
                  <a rel="nofollow" href="//duckduckgo.com/l/?uddg=https%3A%2F%2Fwww.wunderground.com%2Fweather%2Fus%2Fca%2Fsan%2Dfrancisco&amp;rut=ec3b2d335fc4f0596bdd5d5f74bd8138d806636981ec46b3ee0db09bfb3c6abb" class='result-link'>San Francisco, CA Weather Conditions - Weather Underground</a>
                </td>
                
            </tr>

            
              
                <!-- Only show abstract separately if there's a click URL (not EOF) -->
                <tr>
                  <td>&nbsp;&nbsp;&nbsp;</td>
                  <td class='result-snippet'>
                    <b>San</b> <b>Francisco</b> <b>Weather</b> Forecasts. <b>Weather</b> Underground provides local &amp; long-range <b>weather</b> forecasts, weatherreports, maps &amp; tropical <b>weather</b> conditions for the <b>San</b> <b>Francisco</b> area.
                  </td>
                </tr>
              
            

            
              <tr>
                <td>&nbsp;&nbsp;&nbsp;</td>
                <td>
                  <span class='link-text'>www.wunderground.com/weather/us/ca/san-francisco</span>
                  
                </td>
              </tr>
            

            <tr>
              <td>&nbsp;</td>
              <td>&nbsp;</td>
            </tr>

        
      
        
            <tr>
              
                <td valign="top">
                  
                    6.&nbsp;
                  
                </td>
                <td>
                  <a rel="nofollow" href="//duckduckgo.com/l/?uddg=https%3A%2F%2Fworld%2Dweather.info%2Fforecast%2Fusa%2Fsan_francisco%2F14days%2F&amp;rut=827eb5b1322f238a17601cc9572af7dcacdf7d026a45c30caf007f4142f3d7d8" class='result-link'>San Francisco 14 Day Weather Forecast</a>
                </td>
                
            </tr>

            
              
                <!-- Only show abstract separately if there's a click URL (not EOF) -->
                <tr>
                  <td>&nbsp;&nbsp;&nbsp;</td>
                  <td class='result-snippet'>
                    Detailed forecast in <b>San</b> <b>Francisco</b> for 14 days. Air temperature, wind speed, humidity and pressure. <b>Weather</b> forecast in 300000 cities
                  </td>
                </tr>
              
            

            
              <tr>
                <td>&nbsp;&nbsp;&nbsp;</td>
                <td>
                  <span class='link-text'>world-weather.info/forecast/usa/san_francisco/14days/</span>
                  
                    &nbsp;&nbsp;&nbsp;
                    <span class='timestamp'>2026-01-20T00:00:00.0000000</span>
                  
                </td>
              </tr>
            

            <tr>
              <td>&nbsp;</td>
              <td>&nbsp;</td>
            </tr>

        
      
        
            <tr>
              
                <td valign="top">
                  
                    7.&nbsp;
                  
                </td>
                <td>
                  <a rel="nofollow" href="//duckduckgo.com/l/?uddg=https%3A%2F%2Fwww.weatherforyou.com%2Freport%2Fco%2Fsan%2Bfrancisco%2Dextended&amp;rut=6f2902a2b4d224ed6c05c81477a88ba7ed6866f7ba9d762a7e10563346678c5d" class='result-link'>San Francisco, 8 to 14 Day Extended Weather Forecast (°C)</a>
                </td>
                
            </tr>

            
              
                <!-- Only show abstract separately if there's a click URL (not EOF) -->
                <tr>
                  <td>&nbsp;&nbsp;&nbsp;</td>
                  <td class='result-snippet'>
                    Get the 8 to 14 day extended <b>weather</b> forecast for <b>San</b> <b>Francisco</b>, in Celsius. Detailed forecasts with temperature, precipitation, wind and more.
                  </td>
                </tr>
              
            

            
              <tr>
                <td>&nbsp;&nbsp;&nbsp;</td>
                <td>
                  <span class='link-text'>www.weatherforyou.com/report/co/san+francisco-extended</span>
                  
                </td>
              </tr>
            

            <tr>
              <td>&nbsp;</td>
              <td>&nbsp;</td>
            </tr>

        
      
        
            <tr>
              
                <td valign="top">
                  
                    8.&nbsp;
                  
                </td>
                <td>
                  <a rel="nofollow" href="//duckduckgo.com/l/?uddg=https%3A%2F%2Fweather.yahoo.com%2Fus%2Fca%2Fsan%2Dfrancisco&amp;rut=0d5ca90a914500461a2e6c84f999ee16e3caa0c4917aac959143e95a208b1b44" class='result-link'>San Francisco, CA Weather Forecast, Conditions, and Maps - Yahoo Weather</a>
                </td>
                
            </tr>

            
              
                <!-- Only show abstract separately if there's a click URL (not EOF) -->
                <tr>
                  <td>&nbsp;&nbsp;&nbsp;</td>
                  <td class='result-snippet'>
                    Mostly Sunny today with a high of 67°F and a low of 45°F.
                  </td>
                </tr>
              
            

            
              <tr>
                <td>&nbsp;&nbsp;&nbsp;</td>
                <td>
                  <span class='link-text'>weather.yahoo.com/us/ca/san-francisco</span>
                  
                </td>
              </tr>
            

            <tr>
              <td>&nbsp;</td>
              <td>&nbsp;</td>
            </tr>

        
      
        
            <tr>
              
                <td valign="top">
                  
                    9.&nbsp;
                  
                </td>
                <td>
                  <a rel="nofollow" href="//duckduckgo.com/l/?uddg=https%3A%2F%2Fabc7news.com%2Fweather%2F&amp;rut=ba23920d65492fa66217e2f54f7085e5645bc6dcd99f203b25c44333e9a0036a" class='result-link'>Live Doppler 7 | Bay Area Weather News - ABC7 San Francisco</a>
                </td>
                
            </tr>

            
              
                <!-- Only show abstract separately if there's a click URL (not EOF) -->
                <tr>
                  <td>&nbsp;&nbsp;&nbsp;</td>
                  <td class='result-snippet'>
                    No one covers <b>San</b> <b>Francisco</b> <b>weather</b> and the surrounding Bay Area like ABC7. KGO covers forecasts, <b>weather</b> maps, alerts, video, street-level <b>weather</b> and more.
                  </td>
                </tr>
              
            

            
              <tr>
                <td>&nbsp;&nbsp;&nbsp;</td>
                <td>
                  <span class='link-text'>abc7news.com/weather/</span>
                  
                </td>
              </tr>
            

            <tr>
              <td>&nbsp;</td>
              <td>&nbsp;</td>
            </tr>

        
      
        
            <tr>
              
                <td valign="top">
                  
                    10.&nbsp;
                  
                </td>
                <td>
                  <a rel="nofollow" href="//duckduckgo.com/l/?uddg=https%3A%2F%2Fwww.kron4.com%2Fweather%2Dsan%2Dfrancisco%2F&amp;rut=d51fbd0d40adaa9f4857b44c306b39babc48465181029f6d37c3619017f7a3c3" class='result-link'>San Francisco Bay Area Weather | KRON4</a>
                </td>
                
            </tr>

            
              
                <!-- Only show abstract separately if there's a click URL (not EOF) -->
                <tr>
                  <td>&nbsp;&nbsp;&nbsp;</td>
                  <td class='result-snippet'>
                    See the latest <b>San</b> <b>Francisco</b> <b>weather</b> forecast, current conditions, and live radar. Keep up to date on all <b>San</b> <b>Francisco</b> <b>weather</b> news with KRON4.
                  </td>
                </tr>
              
            

            
              <tr>
                <td>&nbsp;&nbsp;&nbsp;</td>
                <td>
                  <span class='link-text'>www.kron4.com/weather-san-francisco/</span>
                  
                </td>
              </tr>
            

            <tr>
              <td>&nbsp;</td>
              <td>&nbsp;</td>
            </tr>

        
      
    

    
      
      <tr>
        <td colspan=2>
          
          
            <!-- Next Page Button Sub-template -->
<form class="next_form" action="/lite/" method="post">
    <input type="submit" class='navbutton' value="Next Page &gt;">
    <input type="hidden" name="q" value="weather san francisco">
    <input type="hidden" name="s" value="10">
    <input type="hidden" name="nextParams" value="">
    <input type="hidden" name="v" value="l">
    <input type="hidden" name="o" value="json">
    <input type="hidden" name="dc" value="11">
    <input type="hidden" name="api" value="d.js">
    <input type="hidden" name="vqd" value="4-191870771335507362608829693484983654615">
    &nbsp;&nbsp;&nbsp;&nbsp;
    
    
    
      <input name="kl" value="wt-wt" type="hidden">
    
    
    
    
</form>
          
          
        </td>
      </tr>
    
  </table>

  <p class='extra'>&nbsp;</p>

  <form action="/lite/" method="post">
      <input class='query' type="text" size="40" name="q" value="weather san francisco" >
      <input class='submit' type="submit" value="Search">
      
      
      
      
      
        <input name="kl" value="wt-wt" type="hidden">
      
  </form>

  <p class='extra'>&nbsp;</p>

  
    <img src="//duckduckgo.com/t/sl_l"/>
  
  <div id='end-spacer'>&nbsp;</div>

</body>
</html>
"""

