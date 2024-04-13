# Help / Frequently Asked Questions

## Troubleshooting errors

### “Invalid or incomplete data received from server” / “Location search failed” / “Weather data refresh failed” / “Weather data refresh for a secondary weather source failed”

The source may be temporarily unavailable, please retry a few hours later. If the problem persists, please open an issue on GitHub.


### “Request timed out”

The source may be temporarily unavailable, please retry later or check your network. If the problem persists and you use a custom DNS, VPN or have a firewall, please check them as well.


### “Required API key missing” / “API requests limit reached” / “API access unauthorized” / “Update not yet available”

For most sources, we only have a limited number of calls allowed for free for all users of our app. If too many users use the same source, the only way to be able to continue using it is to check instructions on the source website to have your own API key. This may be troublesome, but if you have your own API key, the rate-limit will only apply to you (one user vs all users of Breezy Weather).

Regarding the “API access unauthorized”, this error may appear when you subscribed to the wrong product, or you’re trying to use features of the API that your subscription doesn’t allow.


### “Weather source failed to find a matching location”

This error happens when app was able to find your longitude and latitude, but unfortunately, the weather source did not find any location close to this longitude and latitude. Unfortunately, the only workaround is to try with a different source or add your location manually.


### “Failed to parse weather data”

This error should be reported as soon as possible to GitHub, mentioning the source and the location on which it is happening (or for privacy reasons, a nearby location that has the same issue).


### “Source no longer available”

This error may happen when a source is no longer provided by Breezy Weather. In that case, you will need to add a new location with another source, and delete this location. It can also happen when you switch from the standard or `gplay` flavor of Breezy Weather to `fdroid` one which has less sources supported.




## Sources

### How do I change the weather source for a location?

For current location, you can do that with a swipe on the location list, or from the “Edit” button in the footer of the main screen. For manually added locations, you need to add the location again with the other source, and delete the old location.


## Updates

### Background updates are not working

Certain manufacturers implement non-standard Android behaviors, which prevents the app from working properly.

The first thing to try is to whitelist Breezy Weather from battery optimization. From the app, go to Settings > Background updates and tap on “Disable battery optimization” (don’t worry, our background update job is optimized to be very battery-friendly, and you can change “Refresh rate” to “Never” at any time!).

If it still doesn’t work, you can find ways to circumvent aggressive manufacturer behaviors on this website: https://dontkillmyapp.com/


### I used Geometric Weather before, and the “persistent notification” method worked fine for me, can you bring it back?

If you don’t already have a widget, you can try adding one (you don’t need to have it on your main page). On some devices, this may help mimic the old “persistent notification” by avoiding app being killed for no reason, although the widget does not run anything like the old method, it just renders once and updates only on background updates or force refresh from the app.

Otherwise, this “persistent notification” method was based on a foreground service which was running every minute to check if there was something to do.

It is not battery-friendly at all. The worker method that we use just tells Android "we need something to run a task every 1 h 30, but if you are too busy to run it at that moment, you have a 10 minute margin to run it", so it’s much more efficient as Android takes care of running all jobs from all apps by itself at the moment it feels the most appropriate, instead of each app having their own foreground service.

If your manufacturer thinks it’s a good idea to not run scheduled workers but has no problem letting foreground services drain battery, then the problem is the manufacturer, not Breezy Weather, not you.

So we will not bring back/implement “persistent notification” for these reasons:
- it implies writing huge duplicate code (that was known to have duplicate run issues in Geometric Weather, btw) and maintaining it
- it is not battery-friendly

But more generally, we recommend that you follow steps from “Background updates are not working” section to find a workaround.


### Can you make weather refresh less than every 30 minutes / every time I open the app / every time I tap on widget / every time I unlock my phone / every second?

Short answer: no.

Long answer:
Breezy Weather should honor the “refresh rate” setting from Settings > Background updates. If it does not, have a look at troubleshooting above.
If for any reason the background update failed, it will refresh if weather was updated more than “refresh rate time” ago.

If you still want shorter refreshes:
- models are refreshed at best once an hour. Although there might be some little exceptions for some particular data, it’s mostly useless to refresh at intervals less than 30 minutes. Additionally, some providers send header instructions to not contact server again before X (datetime) so you would be served the same cached data anyway.
- we ask for fair usage of API and resources. This app and these API are provided for free and shared by all users of Breezy Weather. Due to noticed abuse, we even had to implement additional caching methods to prevent these abuses and ensure API can still be used by everyone.
- you can still force refresh from main screen by “swiping to refresh”.
