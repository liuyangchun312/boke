const observers = new WeakMap()

export const reveal = {
  mounted(element) {
    if (
      !('IntersectionObserver' in window) ||
      window.matchMedia('(prefers-reduced-motion: reduce)').matches
    )
      return
    element.classList.add('will-reveal')
    const observer = new IntersectionObserver(
      ([entry]) => {
        if (!entry.isIntersecting) return
        element.classList.add('is-revealed')
        observer.disconnect()
      },
      { threshold: 0.08 }
    )
    observer.observe(element)
    observers.set(element, observer)
  },
  beforeUnmount(element) {
    observers.get(element)?.disconnect()
    observers.delete(element)
  }
}
