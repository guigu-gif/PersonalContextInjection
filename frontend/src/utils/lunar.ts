// 农历数据：2020-2035
// newYear: 公历新年 [月, 日]，leapMonth: 闰月序号(0=无)，months: 各月天数
const LUNAR_DATA = [
  { year: 2020, newYear: [1, 25],  leapMonth: 4,  months: [30,30,29,30,29,30,29,30,29,30,29,30,29] },
  { year: 2021, newYear: [2, 12],  leapMonth: 0,  months: [29,30,29,30,29,30,29,30,30,29,30,29] },
  { year: 2022, newYear: [2,  1],  leapMonth: 0,  months: [30,29,30,29,30,30,29,30,29,30,29,30] },
  { year: 2023, newYear: [1, 22],  leapMonth: 2,  months: [30,29,29,30,29,30,29,30,30,29,30,30,29] },
  { year: 2024, newYear: [2, 10],  leapMonth: 6,  months: [30,29,30,29,30,29,30,29,30,29,30,30,29] },
  { year: 2025, newYear: [1, 29],  leapMonth: 6,  months: [30,30,29,30,29,30,29,30,29,30,29,30,29] },
  { year: 2026, newYear: [2, 17],  leapMonth: 0,  months: [30,29,30,29,30,29,30,30,29,30,29,30] },
  { year: 2027, newYear: [2,  6],  leapMonth: 0,  months: [29,30,29,30,29,30,29,30,30,29,30,30] },
  { year: 2028, newYear: [1, 26],  leapMonth: 5,  months: [29,30,29,30,29,30,29,30,29,30,29,30,30] },
  { year: 2029, newYear: [2, 13],  leapMonth: 0,  months: [30,29,30,29,30,29,30,29,30,29,30,29] },
  { year: 2030, newYear: [2,  3],  leapMonth: 3,  months: [30,30,29,30,29,30,29,30,29,30,29,30,29] },
  { year: 2031, newYear: [1, 23],  leapMonth: 0,  months: [30,29,30,29,30,29,30,30,29,30,29,30] },
  { year: 2032, newYear: [2, 11],  leapMonth: 0,  months: [29,30,29,30,29,30,29,30,30,29,30,29] },
  { year: 2033, newYear: [1, 31],  leapMonth: 11, months: [30,29,30,29,30,29,30,29,30,30,29,30,29] },
  { year: 2034, newYear: [2, 19],  leapMonth: 0,  months: [30,29,30,29,30,29,30,29,30,29,30,30] },
  { year: 2035, newYear: [2,  8],  leapMonth: 0,  months: [29,30,29,30,29,30,29,30,29,30,30,29] },
]

const CN_MONTHS = ['正','二','三','四','五','六','七','八','九','十','冬','腊']
const CN_DAYS   = ['初一','初二','初三','初四','初五','初六','初七','初八','初九','初十',
                   '十一','十二','十三','十四','十五','十六','十七','十八','十九','二十',
                   '廿一','廿二','廿三','廿四','廿五','廿六','廿七','廿八','廿九','三十']

function daysBetween(from: Date, to: Date): number {
  return Math.floor((to.getTime() - from.getTime()) / 86400000)
}

export function toLunar(date: Date): string {
  const year = date.getFullYear()
  // 找当年或上一年的农历数据
  let entry = LUNAR_DATA.find(d => d.year === year)
  if (!entry) return ''

  const newYearDate = new Date(year, (entry.newYear[0] ?? 1) - 1, entry.newYear[1] ?? 1)
  let diff = daysBetween(newYearDate, date)

  // 如果在当年春节之前，用上一年数据
  if (diff < 0) {
    const prev = LUNAR_DATA.find(d => d.year === year - 1)
    if (!prev) return ''
    entry = prev
    const prevNewYear = new Date(year - 1, (prev.newYear[0] ?? 1) - 1, prev.newYear[1] ?? 1)
    diff = daysBetween(prevNewYear, date)
  }

  // 遍历各月找到所在月和日
  const { months, leapMonth } = entry
  let lunarMonthIdx = 0   // 在 months 数组中的下标
  let naturalMonth = 1    // 实际农历月序（1-12）
  let isLeap = false

  for (let i = 0; i < months.length; i++) {
    const monthDays = months[i]
    if (monthDays === undefined) break
    if (diff < monthDays) {
      lunarMonthIdx = i
      break
    }
    diff -= monthDays
    // 推进 naturalMonth：闰月不推进月序
    if (leapMonth > 0 && i === leapMonth) {
      // 这个 i 对应的是闰月，不推进
    } else {
      naturalMonth++
    }
  }

  // 判断是否在闰月
  if (leapMonth > 0 && lunarMonthIdx === leapMonth) {
    isLeap = true
  }

  const monthName = (isLeap ? '闰' : '') + CN_MONTHS[naturalMonth - 1] + '月'
  const dayName   = CN_DAYS[diff]
  return `${monthName}${dayName}`
}
