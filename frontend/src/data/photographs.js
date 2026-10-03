const photograph = (name, caption, author, source, license = 'CC BY-SA 4.0') => ({
  image: `/photos/${name}.webp`,
  srcset: `/photos/${name}-640.webp 640w, /photos/${name}.webp 1280w`,
  caption,
  author,
  source,
  license,
  licenseUrl:
    license === 'Public domain'
      ? 'https://creativecommons.org/publicdomain/mark/1.0/'
      : `https://creativecommons.org/licenses/${license === 'CC BY-SA 3.0' ? 'by-sa/3.0' : 'by-sa/4.0'}/`
})

export const photographs = {
  river: photograph(
    'taihe-river',
    '泰和 · 苑前河岸',
    'Windmemories',
    'https://commons.wikimedia.org/wiki/File:20230716_Ci_River_in_Yuanqiang_Town,_Taihe_County.jpg'
  ),
  fields: photograph(
    'taihe-fields',
    '泰和 · 乡村田野',
    'Camphora',
    'https://commons.wikimedia.org/wiki/File:A_country_view_of_Taihe.jpg',
    'Public domain'
  ),
  town: photograph(
    'taihe-town',
    '泰和 · 碧溪镇',
    'MNXANL',
    'https://commons.wikimedia.org/wiki/File:201906_Bixi_Town,_Taihe.jpg'
  ),
  village: photograph(
    'taihe-village',
    '泰和 · 苑前镇街巷',
    'Sinoyuwiki',
    'https://commons.wikimedia.org/wiki/File:Yuanqian_Town_Taihe_County_2025-10-18_(95049).jpg'
  ),
  street: photograph(
    'taihe-street',
    '泰和 · 苑前镇街道',
    'Sinoyuwiki',
    'https://commons.wikimedia.org/wiki/File:Yuanqian_Town_Taihe_County_2025-10-18.jpg'
  ),
  silkie: photograph(
    'silkie',
    '丝羽乌骨鸡 · 品种参考影像，摄于慕尼黑',
    'Diego Delso',
    'https://commons.wikimedia.org/wiki/File:Gallina_Silkie,_Tierpark_Hellabrunn,_M%C3%BAnich,_Alemania,_2012-06-17,_DD_02.JPG',
    'CC BY-SA 3.0'
  )
}

const legacyCovers = {
  '/covers/taihe-kuaige.svg': 'street',
  '/covers/taihe-shukou.svg': 'fields',
  '/covers/taihe-chengjiang.svg': 'town',
  '/covers/taihe-chatanbei.svg': 'river',
  '/covers/taihe-uhji.svg': 'silkie'
}

const articlePhotographs = {
  'taihe-half-day-slow-walk': 'village',
  'chengjiang-morning-market-seasons': 'village',
  'ganjiang-riverside-walking-notes': 'river',
  'taihe-autumn-photography-notes': 'town',
  'shukou-village-respectful-walk': 'town',
  'reading-deng-kuaige-in-taihe': 'river'
}

export const resolvePhotograph = (cover, slug) => {
  const legacy = legacyCovers[cover]
  if (legacy) return photographs[articlePhotographs[slug] || legacy]
  if (!cover) return photographs.fields
  return Object.values(photographs).find((photo) => photo.image === cover) || null
}
