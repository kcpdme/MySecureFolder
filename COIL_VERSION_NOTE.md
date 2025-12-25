# Coil Version Note

## Why Coil 2.7.0 Instead of 3.x?

### The Situation

Coil has two major versions available:
- **Coil 2.x** - Stable, Android-only (`io.coil-kt`)
- **Coil 3.x** - Multiplatform support (`io.coil-kt.coil3`)

### Decision: Using Coil 2.7.0

We're using **Coil 2.7.0** (the latest stable 2.x version) because:

1. **Different Group ID** - Coil 3.x uses `io.coil-kt.coil3` instead of `io.coil-kt`
2. **Breaking API Changes** - Coil 3.x has significant API changes
3. **Our Code Written for 2.x** - All existing code uses Coil 2.x APIs
4. **Stability** - Coil 2.7.0 is battle-tested and stable
5. **Android Only** - We don't need multiplatform support

### Current Configuration

```kotlin
// Coil 2.7.0 (Latest stable 2.x)
implementation("io.coil-kt:coil-compose:2.7.0")
implementation("io.coil-kt:coil-video:2.7.0")
```

### Migration to Coil 3.x (Future)

If you want to migrate to Coil 3.x later, here's what you'll need:

**1. Change Dependencies:**
```kotlin
// Coil 3.x (Multiplatform)
implementation("io.coil-kt.coil3:coil-compose:3.0.0-rc01")
implementation("io.coil-kt.coil3:coil-network-okhttp:3.0.0-rc01")
```

**2. Update Code:**
Coil 3.x has API changes in:
- `AsyncImage` component (mostly compatible)
- `ImageRequest` builder (some changes)
- Network layer (now pluggable)

**3. Test Thoroughly:**
- Image loading
- Video thumbnails
- Error states
- Placeholder behavior

### Coil 2.x Support Timeline

- **Current:** 2.7.0 (released 2024)
- **Support:** Active maintenance
- **EOL:** Not announced yet
- **Recommendation:** Safe to use for several years

### When to Upgrade to Coil 3.x?

Consider upgrading when:
- [ ] Coil 3.x reaches stable (not RC)
- [ ] You need multiplatform support (iOS, Desktop, Web)
- [ ] Coil 2.x reaches end-of-life
- [ ] You need new Coil 3.x exclusive features

For now, **Coil 2.7.0 is the right choice** for this Android-only project.

## Resources

- [Coil 2.x Documentation](https://coil-kt.github.io/coil/)
- [Coil 3.x Migration Guide](https://coil-kt.github.io/coil/upgrading_to_coil3/)
- [Maven: io.coil-kt](https://mvnrepository.com/artifact/io.coil-kt)
- [Maven: io.coil-kt.coil3](https://mvnrepository.com/artifact/io.coil-kt.coil3)

## Summary

✅ **Using Coil 2.7.0** - Latest stable 2.x version
✅ **No code changes needed** - Works with existing code
✅ **Proven stability** - Production-ready
✅ **Long-term support** - Safe for years

You're all set with a solid, stable image loading library!
