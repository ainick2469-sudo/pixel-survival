# Recipe Schema

Recipes remain data-driven from the start. Execution is intentionally deferred, but the schema is locked early so content can scale without rewrites.

## JSON fields

- `id`
- `displayName`
- `category`
- `stationRequired`
- `stationTier`
- `ingredients`
- `outputs`
- `unlockRequirement`
- `tags`
- `craftTimeSeconds`
- `byproducts`
- `notes`

## Example

```json
{
  "id": "pixel_survival:sticks",
  "displayName": "Sticks",
  "category": "primitive",
  "stationRequired": "hand",
  "stationTier": 0,
  "ingredients": [
    { "itemId": "pixel_survival:wood_scrap", "count": 1 }
  ],
  "outputs": [
    { "itemId": "pixel_survival:sticks", "count": 2 }
  ],
  "unlockRequirement": "default",
  "tags": ["foundational", "starter"],
  "craftTimeSeconds": 1.0,
  "byproducts": [],
  "notes": "Starter hand-crafting recipe."
}
```
