<script setup>
import { ArrowLeft, ArrowRight, BookOpen, MapPin, NotebookPen } from 'lucide-vue-next'
import { RouterLink } from 'vue-router'
</script>

<template>
  <main id="main-content" class="about-page">
    <section class="about-hero container-wide">
      <div>
        <p class="eyebrow">ABOUT / 关于</p>
        <h1>刘杨春的<br /><em>泰和乡土手记。</em></h1>
        <p class="about-hero-intro">不是地方百科，也不追赶热门目的地。这里只想从一个长期生活者的视角，把家乡写得准确、具体，也写得有人情。</p>
      </div>
      <div class="about-seal"><span>TAIHE</span><strong>刘</strong><span>FIELD NOTES</span></div>
    </section>

    <section class="about-copy container-narrow">
      <aside class="about-sidebar">
        <div class="about-portrait" aria-hidden="true"><span>刘</span><small>LIU YANGCHUN</small></div>
        <dl>
          <div><dt><MapPin :size="14" />坐标</dt><dd>江西 · 吉安 · 泰和</dd></div>
          <div><dt><NotebookPen :size="14" />在写</dt><dd>乡土、人文与日常</dd></div>
          <div><dt><BookOpen :size="14" />更新</dt><dd>不定期，认真写完再发</dd></div>
        </dl>
      </aside>

      <div class="about-main">
        <div class="about-kicker"><BookOpen :size="20" /><span>从家乡出发，把日子写下来。</span></div>
        <div class="about-prose">
          <p class="about-lead">我是刘杨春。这里收录我从泰和出发写下的乡土手记。</p>
          <p>我写快阁的月色、武山的白凤乌鸡、赣江边的风，也写泰和街巷里真实而普通的一天。那些容易被匆忙略过的地方风物与日常，值得被认真记下。</p>

          <h2>为什么写这本手记</h2>
          <p>一座县城常常被几句简介概括，但真正构成家乡的，是人如何生活、地名怎样被念起、旧建筑与今天发生了什么联系。我希望每篇文章都从具体的问题和现场出发，不把泰和写成一张没有人的明信片。</p>

          <h2>我如何写</h2>
          <p>涉及历史沿革、人物和文化遗产时，我会尽量核对公开资料，并清楚区分史实、地方传说与个人感受。日常书写则保留它本来的温度：允许缓慢，也允许一篇文章只认真说好一件小事。</p>
        </div>

        <div class="about-principles">
          <div><span>01</span><strong>具体</strong><p>从真实地点、人物和细节写起。</p></div>
          <div><span>02</span><strong>克制</strong><p>不夸张功效，也不消费乡愁。</p></div>
          <div><span>03</span><strong>长久</strong><p>把网站当作一本持续生长的地方手记。</p></div>
        </div>

        <RouterLink class="text-link" :to="{ name: 'home' }"><ArrowLeft :size="17" />回到文章索引</RouterLink>
        <RouterLink class="about-archive-link" :to="{ name: 'archives' }">按时间翻阅全部手记<ArrowRight :size="17" /></RouterLink>
      </div>
    </section>
  </main>
</template>
