## Localization

```
<resource type>-b+<language code>[+<country code>]
```

The locale is a combination of the language and the country. The language is defined by the [ISO 639-1](https://en.wikipedia.org/wiki/ISO_639-1) standard while the country is defined by the [ISO 3166-1](https://en.wikipedia.org/wiki/ISO_3166-1) standard.


Locale change is not easy to implement at runtime. Just prepare localized resources and leave it to the system to handle.

### Localize Chinese Language

```
values-b+zh+CN
values-b+zh+TW
values-b+zh+HK
values-b+zh+MO
```

## Server-side App Launch Ad API

* It should use HTTP header (like etag) to tell client whether the resource is outdated and client should update local data;

* Use JSON. Use meaningful JSON.

DO NOT USE strings `yes` or `no` to indicate Boolean values! Use `true` or `false` please.

DO NOT USE strings for numeric values! Use number as number. Why convert numbers to strings and force client to do the conversion back?

DO NOT USE strings for array. If you mean an array, use an array. DO NOT USE a dot/comma...-separated string as array.

JSON keys should indicated the meaning of their value.