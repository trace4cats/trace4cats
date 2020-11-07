# Filtering

Filtering allows certain sensitive information to be removed or redacted from trace attributes, in Trace4cats this is
generally performed in the collectors.

The [collector] can be configured to remove attributes based on names, values, or name-value pairs:

```yaml
attribute-filtering:
  names: # remove any attribute whose name matches one of these 
    - some.attribute.name
    - another.attribute.name
  values: # filter any attribute whose value contains one of these
    - prohibited
  name-values: # filter any attribute whose name and value matches one of these
    some.attribute.name: prohibited
```

[collector]: components.md#collector