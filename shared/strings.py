#!/usr/bin/env python3
"""UI strings in 7 languages -> iOS Localizable.strings + Android strings.xml.
Placeholders: {0}, {1}. Order: en, bg, de, fr, es, tr, ru."""
import os

LANGS = ["en", "bg", "de", "fr", "es", "tr", "ru"]

S = {
 "app_name": ["WeightKit", "WeightKit", "WeightKit", "WeightKit", "WeightKit", "WeightKit", "WeightKit"],
 "tab_calc": ["Calculator", "Калкулатор", "Rechner", "Calcul", "Calculadora", "Hesapla", "Калькулятор"],
 "tab_lists": ["Lists", "Списъци", "Listen", "Listes", "Listas", "Listeler", "Списки"],
 "tab_settings": ["Settings", "Настройки", "Einstellungen", "Réglages", "Ajustes", "Ayarlar", "Настройки"],

 "shape": ["Shape", "Форма", "Form", "Forme", "Forma", "Şekil", "Форма"],
 "shape_plate": ["Sheet / plate", "Лист / ламарина", "Blech / Platte", "Tôle / plaque", "Chapa / placa", "Sac / levha", "Лист"],
 "shape_round": ["Round bar", "Кръгла шина", "Rundstab", "Rond plein", "Barra redonda", "Yuvarlak çubuk", "Круг"],
 "shape_square": ["Square bar", "Квадратна шина", "Vierkantstab", "Carré plein", "Barra cuadrada", "Kare çubuk", "Квадрат"],
 "shape_hex": ["Hex bar", "Шестостенна шина", "Sechskantstab", "Hexagone", "Barra hexagonal", "Altıgen çubuk", "Шестигранник"],
 "shape_flat": ["Flat bar", "Плоска шина", "Flachstab", "Plat", "Pletina", "Lama", "Полоса"],
 "shape_pipe": ["Round tube / pipe", "Кръгла тръба", "Rundrohr", "Tube rond", "Tubo redondo", "Boru", "Труба круглая"],
 "shape_rect_tube": ["Square / rect. tube", "Профилна тръба", "Vierkantrohr", "Tube carré / rect.", "Tubo cuadrado / rect.", "Kutu profil", "Труба профильная"],
 "shape_angle": ["Angle (L)", "Ъглов профил (L)", "Winkel (L)", "Cornière (L)", "Ángulo (L)", "Köşebent (L)", "Уголок (L)"],
 "shape_channel": ["Channel (U)", "U-профил", "U-Profil", "Profil U", "Perfil U", "U profil", "Швеллер (U)"],
 "shape_ibeam": ["I / H beam", "I / H профил", "I / H-Träger", "Poutre I / H", "Viga I / H", "I / H profil", "Балка I / H"],
 "shape_tee": ["T-section", "T-профил", "T-Profil", "Profil T", "Perfil T", "T profil", "Тавр (T)"],
 "shape_rebar": ["Rebar", "Арматура", "Betonstahl", "Fer à béton", "Varilla corrugada", "İnşaat demiri", "Арматура"],
 "shape_section": ["Standard profiles", "Стандартни профили", "Normprofile", "Profilés normalisés", "Perfiles estándar", "Standart profiller", "Сортамент"],

 "dim_thickness": ["Thickness", "Дебелина", "Dicke", "Épaisseur", "Espesor", "Kalınlık", "Толщина"],
 "dim_width": ["Width", "Ширина", "Breite", "Largeur", "Ancho", "Genişlik", "Ширина"],
 "dim_length": ["Length", "Дължина", "Länge", "Longueur", "Longitud", "Uzunluk", "Длина"],
 "dim_diameter": ["Diameter", "Диаметър", "Durchmesser", "Diamètre", "Diámetro", "Çap", "Диаметр"],
 "dim_od": ["Outer diameter", "Външен диаметър", "Außendurchmesser", "Diamètre ext.", "Diámetro ext.", "Dış çap", "Наружный диаметр"],
 "dim_wall": ["Wall thickness", "Дебелина на стената", "Wandstärke", "Épaisseur paroi", "Espesor pared", "Et kalınlığı", "Толщина стенки"],
 "dim_side": ["Side", "Страна", "Seitenlänge", "Côté", "Lado", "Kenar", "Сторона"],
 "dim_af": ["Across flats", "Под ключ", "Schlüsselweite", "Sur plats", "Entre caras", "Anahtar ağzı", "Под ключ"],
 "dim_height": ["Height", "Височина", "Höhe", "Hauteur", "Altura", "Yükseklik", "Высота"],
 "dim_flange_width": ["Flange width", "Ширина на пояса", "Flanschbreite", "Largeur aile", "Ancho ala", "Başlık genişliği", "Ширина полки"],
 "dim_web": ["Web thickness", "Дебелина на стеблото", "Stegdicke", "Épaisseur âme", "Espesor alma", "Gövde kalınlığı", "Толщина стенки"],
 "dim_flange": ["Flange thickness", "Дебелина на пояса", "Flanschdicke", "Épaisseur aile", "Espesor ala", "Başlık kalınlığı", "Толщина полки"],
 "dim_leg_a": ["Leg A", "Рамо A", "Schenkel A", "Aile A", "Ala A", "Kol A", "Полка A"],
 "dim_leg_b": ["Leg B", "Рамо B", "Schenkel B", "Aile B", "Ala B", "Kol B", "Полка B"],

 "material": ["Material", "Материал", "Werkstoff", "Matériau", "Material", "Malzeme", "Материал"],
 "search": ["Search", "Търсене", "Suchen", "Rechercher", "Buscar", "Ara", "Поиск"],
 "density": ["Density", "Плътност", "Dichte", "Masse volumique", "Densidad", "Yoğunluk", "Плотность"],
 "quantity": ["Quantity", "Брой", "Stückzahl", "Quantité", "Cantidad", "Adet", "Количество"],
 "results": ["Result", "Резултат", "Ergebnis", "Résultat", "Resultado", "Sonuç", "Результат"],
 "weight_piece": ["Per piece", "За брой", "Pro Stück", "Par pièce", "Por pieza", "Adet başı", "За штуку"],
 "weight_per_m": ["Per metre", "На метър", "Pro Meter", "Par mètre", "Por metro", "Metre başı", "На метр"],
 "weight_per_ft": ["Per foot", "На фут", "Pro Fuß", "Par pied", "Por pie", "Fit başı", "На фут"],
 "total_weight": ["Total weight", "Общо тегло", "Gesamtgewicht", "Poids total", "Peso total", "Toplam ağırlık", "Общий вес"],
 "volume": ["Volume", "Обем", "Volumen", "Volume", "Volumen", "Hacim", "Объём"],
 "surface": ["Surface area", "Повърхност", "Oberfläche", "Surface", "Superficie", "Yüzey alanı", "Площадь поверхности"],
 "cost": ["Cost", "Цена", "Kosten", "Coût", "Coste", "Maliyet", "Стоимость"],
 "price_per_kg": ["Price per kg", "Цена за кг", "Preis pro kg", "Prix par kg", "Precio por kg", "kg fiyatı", "Цена за кг"],
 "price_per_lb": ["Price per lb", "Цена за lb", "Preis pro lb", "Prix par lb", "Precio por lb", "lb fiyatı", "Цена за lb"],
 "invalid_input": ["Enter the dimensions to see the weight", "Въведете размерите, за да видите теглото", "Maße eingeben, um das Gewicht zu sehen", "Saisissez les dimensions pour voir le poids", "Introduce las medidas para ver el peso", "Ağırlığı görmek için ölçüleri girin", "Введите размеры, чтобы увидеть вес"],
 "invalid_wall": ["Wall is too thick for this size", "Стената е твърде дебела за този размер", "Wand ist zu dick für dieses Maß", "Paroi trop épaisse pour cette dimension", "Pared demasiado gruesa para esta medida", "Et bu ölçü için çok kalın", "Стенка слишком толстая для этого размера"],

 "reverse_title": ["Reverse: from weight", "Обратно: от тегло", "Rückwärts: aus Gewicht", "Inverse : depuis le poids", "Inverso: desde el peso", "Ters: ağırlıktan", "Обратный расчёт: от веса"],
 "target_weight": ["Target weight", "Желано тегло", "Zielgewicht", "Poids cible", "Peso objetivo", "Hedef ağırlık", "Требуемый вес"],
 "needed_length": ["Length needed", "Нужна дължина", "Benötigte Länge", "Longueur nécessaire", "Longitud necesaria", "Gereken uzunluk", "Нужная длина"],
 "needed_thickness": ["Thickness needed", "Нужна дебелина", "Benötigte Dicke", "Épaisseur nécessaire", "Espesor necesario", "Gereken kalınlık", "Нужная толщина"],
 "pieces_in_target": ["Whole pieces", "Цели бройки", "Ganze Stücke", "Pièces entières", "Piezas enteras", "Tam adet", "Целых штук"],

 "standard": ["Standard", "Стандарт", "Norm", "Norme", "Norma", "Standart", "Стандарт"],
 "profile": ["Profile", "Профил", "Profil", "Profilé", "Perfil", "Profil", "Профиль"],
 "table_mass": ["Table (steel): {0}", "По таблица (стомана): {0}", "Tabelle (Stahl): {0}", "Tableau (acier) : {0}", "Tabla (acero): {0}", "Tablo (çelik): {0}", "По таблице (сталь): {0}"],
 "area": ["Cross-section area", "Площ на сечението", "Querschnittsfläche", "Section", "Área de sección", "Kesit alanı", "Площадь сечения"],
 "data_note": ["Section tables are from the cited standards. Check critical orders against the official table.", "Таблиците са по посочените стандарти. При важни поръчки сверете с официалната таблица.", "Profiltabellen nach den genannten Normen. Bei wichtigen Bestellungen mit der offiziellen Tabelle prüfen.", "Tableaux selon les normes citées. Vérifiez les commandes importantes avec le tableau officiel.", "Tablas según las normas citadas. Verifique pedidos importantes con la tabla oficial.", "Tablolar belirtilen standartlara göredir. Önemli siparişleri resmi tabloyla kontrol edin.", "Таблицы по указанным стандартам. Важные заказы сверяйте с официальной таблицей."],

 "add_to_list": ["Add to list", "Добави в списък", "Zur Liste", "Ajouter à la liste", "Añadir a la lista", "Listeye ekle", "В список"],
 "added_to": ["Added to “{0}”", "Добавено в „{0}“", "Zu „{0}“ hinzugefügt", "Ajouté à « {0} »", "Añadido a «{0}»", "“{0}” listesine eklendi", "Добавлено в «{0}»"],
 "lists_empty": ["No lists yet. Calculate an item and tap “Add to list”.", "Няма списъци. Изчислете позиция и натиснете „Добави в списък“.", "Noch keine Listen. Berechnen Sie ein Teil und tippen Sie auf „Zur Liste“.", "Aucune liste. Calculez une pièce et touchez « Ajouter à la liste ».", "Aún no hay listas. Calcula una pieza y pulsa «Añadir a la lista».", "Henüz liste yok. Bir parça hesaplayıp “Listeye ekle”ye dokunun.", "Списков пока нет. Рассчитайте позицию и нажмите «В список»."],
 "new_list": ["New list", "Нов списък", "Neue Liste", "Nouvelle liste", "Nueva lista", "Yeni liste", "Новый список"],
 "list_name": ["List name", "Име на списъка", "Listenname", "Nom de la liste", "Nombre de la lista", "Liste adı", "Название списка"],
 "default_list": ["My list", "Моят списък", "Meine Liste", "Ma liste", "Mi lista", "Listem", "Мой список"],
 "items": ["Items", "Позиции", "Positionen", "Lignes", "Partidas", "Kalemler", "Позиции"],
 "item": ["Item", "Позиция", "Position", "Article", "Partida", "Kalem", "Позиция"],
 "size": ["Size", "Размер", "Abmessung", "Dimension", "Medida", "Ölçü", "Размер"],
 "qty": ["Qty", "Бр.", "Stk.", "Qté", "Cant.", "Adet", "Кол."],
 "weight": ["Weight", "Тегло", "Gewicht", "Poids", "Peso", "Ağırlık", "Вес"],
 "waste": ["Waste / scrap %", "Отпадък %", "Verschnitt %", "Chute %", "Merma %", "Fire %", "Отходы %"],
 "markup": ["Markup %", "Надценка %", "Aufschlag %", "Marge %", "Margen %", "Kâr marjı %", "Наценка %"],
 "subtotal": ["Subtotal", "Междинна сума", "Zwischensumme", "Sous-total", "Subtotal", "Ara toplam", "Промежуточный итог"],
 "total": ["Total", "Общо", "Gesamt", "Total", "Total", "Toplam", "Итого"],
 "export_pdf": ["Export PDF", "Експорт PDF", "PDF exportieren", "Exporter PDF", "Exportar PDF", "PDF dışa aktar", "Экспорт PDF"],
 "export_csv": ["Export CSV", "Експорт CSV", "CSV exportieren", "Exporter CSV", "Exportar CSV", "CSV dışa aktar", "Экспорт CSV"],
 "delete": ["Delete", "Изтрий", "Löschen", "Supprimer", "Eliminar", "Sil", "Удалить"],
 "save": ["Save", "Запази", "Speichern", "Enregistrer", "Guardar", "Kaydet", "Сохранить"],
 "cancel": ["Cancel", "Отказ", "Abbrechen", "Annuler", "Cancelar", "İptal", "Отмена"],
 "done": ["Done", "Готово", "Fertig", "OK", "Listo", "Tamam", "Готово"],
 "date": ["Date", "Дата", "Datum", "Date", "Fecha", "Tarih", "Дата"],
 "pdf_title": ["Material list", "Списък с материали", "Materialliste", "Liste de matériaux", "Lista de materiales", "Malzeme listesi", "Спецификация металла"],

 "units": ["Units", "Мерни единици", "Einheiten", "Unités", "Unidades", "Birimler", "Единицы"],
 "metric": ["Metric (mm, m, kg)", "Метрични (mm, m, kg)", "Metrisch (mm, m, kg)", "Métrique (mm, m, kg)", "Métrico (mm, m, kg)", "Metrik (mm, m, kg)", "Метрические (мм, м, кг)"],
 "imperial": ["Imperial (in, ft, lb)", "Имперски (in, ft, lb)", "Zoll (in, ft, lb)", "Impérial (in, ft, lb)", "Imperial (in, ft, lb)", "İnç (in, ft, lb)", "Дюймовые (in, ft, lb)"],
 "currency": ["Currency symbol", "Символ на валутата", "Währungssymbol", "Symbole monétaire", "Símbolo de moneda", "Para birimi simgesi", "Символ валюты"],
 "company": ["Company name (for PDF)", "Име на фирмата (за PDF)", "Firmenname (für PDF)", "Nom de l’entreprise (PDF)", "Nombre de la empresa (PDF)", "Firma adı (PDF için)", "Название компании (для PDF)"],
 "company_pdf": ["Company details (for PDF)", "Фирма (за PDF)", "Firmendaten (für PDF)", "Entreprise (pour le PDF)", "Empresa (para el PDF)", "Firma bilgileri (PDF için)", "Компания (для PDF)"],
 "company_name": ["Company name", "Име на фирмата", "Firmenname", "Nom de l’entreprise", "Nombre de la empresa", "Firma adı", "Название компании"],
 "company_address": ["Address", "Адрес", "Adresse", "Adresse", "Dirección", "Adres", "Адрес"],
 "company_contact": ["Phone / e-mail / VAT No.", "Телефон / e-mail / ЕИК", "Telefon / E-Mail / USt-IdNr.", "Téléphone / e-mail / N° TVA", "Teléfono / e-mail / NIF", "Telefon / e-posta / Vergi No", "Телефон / e-mail / ИНН"],
 "price_per_m": ["Price per m", "Цена за метър", "Preis pro m", "Prix par m", "Precio por m", "Metre fiyatı", "Цена за метр"],
 "price_per_ft": ["Price per ft", "Цена за ft", "Preis pro ft", "Prix par ft", "Precio por ft", "ft fiyatı", "Цена за ft"],
 "price_per_piece": ["Price per piece", "Цена за брой", "Preis pro Stück", "Prix par pièce", "Precio por pieza", "Adet fiyatı", "Цена за штуку"],
 "price_unit_kg": ["per kg", "за кг", "pro kg", "par kg", "por kg", "kg başına", "за кг"],
 "price_unit_lb": ["per lb", "за lb", "pro lb", "par lb", "por lb", "lb başına", "за lb"],
 "price_unit_m": ["per m", "за м", "pro m", "par m", "por m", "metre başına", "за м"],
 "price_unit_ft": ["per ft", "за ft", "pro ft", "par ft", "por ft", "ft başına", "за ft"],
 "price_unit_pc": ["per piece", "за брой", "pro Stück", "par pièce", "por pieza", "adet başına", "за шт."],
 "quote": ["Quote details (PDF)", "Оферта (за PDF)", "Angebot (PDF)", "Devis (PDF)", "Presupuesto (PDF)", "Teklif (PDF)", "Предложение (PDF)"],
 "quote_title": ["Quote", "Оферта", "Angebot", "Devis", "Presupuesto", "Teklif", "Коммерческое предложение"],
 "client": ["Client", "Клиент", "Kunde", "Client", "Cliente", "Müşteri", "Клиент"],
 "quote_no": ["Quote no.", "Оферта №", "Angebot Nr.", "Devis n°", "Presupuesto n.º", "Teklif no", "Предложение №"],
 "vat": ["VAT", "ДДС", "MwSt.", "TVA", "IVA", "KDV", "НДС"],
 "vat_pct": ["VAT %", "ДДС %", "MwSt. %", "TVA %", "IVA %", "KDV %", "НДС %"],
 "total_ex_vat": ["Total excl. VAT", "Общо без ДДС", "Summe netto", "Total HT", "Total sin IVA", "KDV hariç toplam", "Итого без НДС"],
 "total_with_vat": ["Total incl. VAT", "Общо с ДДС", "Summe brutto", "Total TTC", "Total con IVA", "KDV dahil toplam", "Итого с НДС"],
 "valid_days": ["Valid for (days)", "Валидна (дни)", "Gültig (Tage)", "Valable (jours)", "Válido (días)", "Geçerlilik (gün)", "Действует (дней)"],
 "valid_until": ["Valid until", "Валидна до", "Gültig bis", "Valable jusqu’au", "Válido hasta", "Geçerlilik tarihi", "Действует до"],
 "notes": ["Notes / terms", "Бележки / условия", "Hinweise / Bedingungen", "Notes / conditions", "Notas / condiciones", "Notlar / şartlar", "Примечания / условия"],
 "duplicate": ["Duplicate list", "Дублирай списъка", "Liste duplizieren", "Dupliquer la liste", "Duplicar lista", "Listeyi çoğalt", "Дублировать список"],
 "copy_suffix": ["copy", "копие", "Kopie", "copie", "copia", "kopya", "копия"],
 "more": ["More", "Още", "Mehr", "Plus", "Más", "Daha fazla", "Ещё"],
 "backup_export": ["Back up all lists (file)", "Архив на всички списъци (файл)", "Alle Listen sichern (Datei)", "Sauvegarder toutes les listes (fichier)", "Copia de todas las listas (archivo)", "Tüm listeleri yedekle (dosya)", "Резервная копия всех списков (файл)"],
 "backup_import": ["Restore lists from file", "Възстанови списъци от файл", "Listen aus Datei wiederherstellen", "Restaurer des listes depuis un fichier", "Restaurar listas desde archivo", "Listeleri dosyadan geri yükle", "Восстановить списки из файла"],
 "backup_done": ["Lists restored: {0}", "Възстановени списъци: {0}", "Wiederhergestellte Listen: {0}", "Listes restaurées : {0}", "Listas restauradas: {0}", "Geri yüklenen listeler: {0}", "Восстановлено списков: {0}"],
 "backup_bad": ["This is not a WeightKit backup file", "Това не е архив от WeightKit", "Das ist keine WeightKit-Sicherung", "Ce n’est pas une sauvegarde WeightKit", "No es una copia de WeightKit", "Bu bir WeightKit yedeği değil", "Это не резервная копия WeightKit"],
 "logo": ["Logo (for PDF)", "Лого (за PDF)", "Logo (für PDF)", "Logo (pour le PDF)", "Logo (para el PDF)", "Logo (PDF için)", "Логотип (для PDF)"],
 "logo_pick": ["Choose logo", "Избери лого", "Logo wählen", "Choisir un logo", "Elegir logo", "Logo seç", "Выбрать логотип"],
 "logo_remove": ["Remove logo", "Премахни логото", "Logo entfernen", "Supprimer le logo", "Quitar logo", "Logoyu kaldır", "Удалить логотип"],
 "logo_bad": ["This image could not be read", "Изображението не може да се прочете", "Das Bild konnte nicht gelesen werden", "Impossible de lire cette image", "No se pudo leer la imagen", "Bu görsel okunamadı", "Не удалось прочитать изображение"],
 "theme": ["Appearance", "Изглед", "Darstellung", "Apparence", "Apariencia", "Görünüm", "Оформление"],
 "theme_system": ["System", "Системен", "System", "Système", "Sistema", "Sistem", "Системное"],
 "theme_light": ["Light", "Светъл", "Hell", "Clair", "Claro", "Açık", "Светлое"],
 "theme_dark": ["Dark", "Тъмен", "Dunkel", "Sombre", "Oscuro", "Koyu", "Тёмное"],
 "custom_materials": ["Custom materials", "Собствени материали", "Eigene Werkstoffe", "Matériaux personnalisés", "Materiales propios", "Özel malzemeler", "Свои материалы"],
 "add_material": ["Add material", "Добави материал", "Werkstoff hinzufügen", "Ajouter un matériau", "Añadir material", "Malzeme ekle", "Добавить материал"],
 "name": ["Name", "Име", "Name", "Nom", "Nombre", "Ad", "Название"],
 "density_unit_hint": ["Density in kg/m³", "Плътност в kg/m³", "Dichte in kg/m³", "Masse volumique en kg/m³", "Densidad en kg/m³", "kg/m³ cinsinden yoğunluk", "Плотность в кг/м³"],
 "group_steel": ["Steel & iron", "Стомана и чугун", "Stahl & Eisen", "Acier & fonte", "Acero y hierro", "Çelik ve demir", "Сталь и чугун"],
 "group_stainless": ["Stainless steel", "Неръждаема стомана", "Edelstahl", "Inox", "Acero inoxidable", "Paslanmaz çelik", "Нержавеющая сталь"],
 "group_aluminium": ["Aluminium", "Алуминий", "Aluminium", "Aluminium", "Aluminio", "Alüminyum", "Алюминий"],
 "group_copper": ["Copper alloys", "Мед и сплави", "Kupferlegierungen", "Cuivreux", "Aleaciones de cobre", "Bakır alaşımları", "Медь и сплавы"],
 "group_other": ["Other metals", "Други метали", "Andere Metalle", "Autres métaux", "Otros metales", "Diğer metaller", "Другие металлы"],
 "group_plastic": ["Plastics", "Пластмаси", "Kunststoffe", "Plastiques", "Plásticos", "Plastikler", "Пластики"],
 "group_custom": ["My materials", "Моите материали", "Meine Werkstoffe", "Mes matériaux", "Mis materiales", "Malzemelerim", "Мои материалы"],

 "pro_title": ["WeightKit Pro", "WeightKit Pro", "WeightKit Pro", "WeightKit Pro", "WeightKit Pro", "WeightKit Pro", "WeightKit Pro"],
 "pro_desc": ["One-time purchase, no subscription. Unlocks saved lists, PDF quotes with your logo, VAT and client, CSV export, price per metre or piece, list backup, reverse calculation, all section tables, custom materials and removes ads.", "Еднократна покупка, без абонамент. Отключва записани списъци, PDF оферти с лого, ДДС и клиент, CSV експорт, цена за метър или брой, архив на списъците, обратно изчисление, всички таблици с профили, собствени материали и премахва рекламите.", "Einmalkauf, kein Abo. Schaltet gespeicherte Listen, PDF-Angebote mit Logo, MwSt. und Kunde, CSV-Export, Preis pro Meter oder Stück, Listen-Backup, Rückwärtsrechnung, alle Profiltabellen und eigene Werkstoffe frei und entfernt Werbung.", "Achat unique, sans abonnement. Débloque les listes, les devis PDF avec logo, TVA et client, l’export CSV, le prix au mètre ou à la pièce, la sauvegarde des listes, le calcul inverse, tous les profilés, les matériaux personnalisés et supprime la publicité.", "Compra única, sin suscripción. Desbloquea listas guardadas, presupuestos PDF con logo, IVA y cliente, exportación CSV, precio por metro o por pieza, copia de las listas, cálculo inverso, todas las tablas de perfiles, materiales propios y quita los anuncios.", "Tek seferlik satın alma, abonelik yok. Kayıtlı listeler, logolu, KDV’li ve müşterili PDF teklifler, CSV dışa aktarma, metre veya adet fiyatı, liste yedeği, ters hesaplama, tüm profil tabloları, özel malzemeler açılır ve reklamlar kalkar.", "Разовая покупка, без подписки. Открывает сохранённые списки, PDF-предложения с логотипом, НДС и клиентом, экспорт CSV, цену за метр или штуку, резервную копию списков, обратный расчёт, все таблицы сортамента, свои материалы и убирает рекламу."],
 "buy_pro": ["Unlock Pro – {0}", "Отключи Pro – {0}", "Pro freischalten – {0}", "Débloquer Pro – {0}", "Desbloquear Pro – {0}", "Pro’yu aç – {0}", "Открыть Pro – {0}"],
 "buy_pro_noprice": ["Unlock Pro", "Отключи Pro", "Pro freischalten", "Débloquer Pro", "Desbloquear Pro", "Pro’yu aç", "Открыть Pro"],
 "restore": ["Restore purchase", "Възстанови покупка", "Kauf wiederherstellen", "Restaurer l’achat", "Restaurar compra", "Satın alımı geri yükle", "Восстановить покупку"],
 "pro_active": ["Pro unlocked. Thank you!", "Pro е отключен. Благодарим!", "Pro freigeschaltet. Danke!", "Pro débloqué. Merci !", "Pro desbloqueado. ¡Gracias!", "Pro açıldı. Teşekkürler!", "Pro открыт. Спасибо!"],
 "start_trial": ["Start 7-day free trial", "Започни 7 дни безплатно", "7 Tage kostenlos testen", "Essai gratuit de 7 jours", "Prueba gratis de 7 días", "7 gün ücretsiz dene", "Попробовать 7 дней бесплатно"],
 "trial_terms": ["Try every Pro feature free for 7 days. No payment, no subscription. When the trial ends, the Pro features lock again unless you buy Pro for {0} (one-time purchase).", "Изпробвай всички Pro функции безплатно за 7 дни. Без плащане и без абонамент. След края на пробния период Pro функциите се заключват, освен ако не купиш Pro за {0} (еднократна покупка).", "Alle Pro-Funktionen 7 Tage kostenlos testen. Ohne Zahlung, ohne Abo. Nach dem Test werden die Pro-Funktionen wieder gesperrt, außer du kaufst Pro für {0} (Einmalkauf).", "Essayez toutes les fonctions Pro gratuitement pendant 7 jours. Sans paiement ni abonnement. À la fin de l’essai, les fonctions Pro sont de nouveau verrouillées, sauf si vous achetez Pro pour {0} (achat unique).", "Prueba todas las funciones Pro gratis durante 7 días. Sin pago ni suscripción. Al terminar la prueba, las funciones Pro se bloquean de nuevo salvo que compres Pro por {0} (compra única).", "Tüm Pro özelliklerini 7 gün ücretsiz deneyin. Ödeme yok, abonelik yok. Deneme bitince Pro özellikleri yeniden kilitlenir; açık kalması için Pro’yu {0} karşılığında satın alın (tek seferlik).", "Все функции Pro бесплатно на 7 дней. Без оплаты и без подписки. После пробного периода функции Pro снова закрываются, если не купить Pro за {0} (разовая покупка)."],
 "trial_terms_noprice": ["Try every Pro feature free for 7 days. No payment, no subscription. When the trial ends, the Pro features lock again unless you buy Pro (one-time purchase).", "Изпробвай всички Pro функции безплатно за 7 дни. Без плащане и без абонамент. След края на пробния период Pro функциите се заключват, освен ако не купиш Pro (еднократна покупка).", "Alle Pro-Funktionen 7 Tage kostenlos testen. Ohne Zahlung, ohne Abo. Nach dem Test werden die Pro-Funktionen wieder gesperrt, außer du kaufst Pro (Einmalkauf).", "Essayez toutes les fonctions Pro gratuitement pendant 7 jours. Sans paiement ni abonnement. À la fin de l’essai, les fonctions Pro sont de nouveau verrouillées, sauf si vous achetez Pro (achat unique).", "Prueba todas las funciones Pro gratis durante 7 días. Sin pago ni suscripción. Al terminar la prueba, las funciones Pro se bloquean de nuevo salvo que compres Pro (compra única).", "Tüm Pro özelliklerini 7 gün ücretsiz deneyin. Ödeme yok, abonelik yok. Deneme bitince Pro özellikleri yeniden kilitlenir; açık kalması için Pro’yu satın alın (tek seferlik).", "Все функции Pro бесплатно на 7 дней. Без оплаты и без подписки. После пробного периода функции Pro снова закрываются, если не купить Pro (разовая покупка)."],
 "trial_over": ["Your free trial has ended", "Безплатният пробен период изтече", "Dein kostenloser Test ist beendet", "Votre essai gratuit est terminé", "Tu prueba gratuita ha terminado", "Ücretsiz deneme süreniz bitti", "Пробный период закончился"],
 "trial_active": ["Free Pro trial until {0}", "Безплатен Pro до {0}", "Pro kostenlos testen bis {0}", "Essai Pro gratuit jusqu’au {0}", "Prueba Pro gratis hasta el {0}", "{0} tarihine kadar ücretsiz Pro", "Бесплатный Pro до {0}"],
 "trial_notice": ["Pro feature – trial ends {0}", "Pro функция – пробният период изтича на {0}", "Pro-Funktion – Test endet am {0}", "Fonction Pro – l’essai se termine le {0}", "Función Pro – la prueba termina el {0}", "Pro özellik – deneme {0} tarihinde biter", "Функция Pro – пробный период до {0}"],
 "pro_locked": ["This is a Pro feature", "Това е Pro функция", "Das ist eine Pro-Funktion", "Fonction Pro", "Función Pro", "Bu bir Pro özelliği", "Это функция Pro"],
 "pro_badge": ["PRO", "PRO", "PRO", "PRO", "PRO", "PRO", "PRO"],
 "purchase_failed": ["Purchase could not be completed.", "Покупката не беше завършена.", "Kauf konnte nicht abgeschlossen werden.", "L’achat n’a pas pu aboutir.", "No se pudo completar la compra.", "Satın alma tamamlanamadı.", "Покупка не завершена."],
 "reward_offer": ["Watch a short ad – Pro free for 24 h", "Гледай кратка реклама – Pro безплатно за 24 ч", "Kurze Werbung ansehen – 24 h Pro gratis", "Regarder une courte pub – Pro gratuit 24 h", "Ver un anuncio corto – Pro gratis 24 h", "Kısa reklam izle – 24 saat ücretsiz Pro", "Посмотреть рекламу – Pro бесплатно на 24 ч"],
 "reward_active": ["Pro unlocked for 24 h", "Pro е отключен за 24 ч", "Pro für 24 h freigeschaltet", "Pro débloqué pour 24 h", "Pro desbloqueado 24 h", "Pro 24 saatliğine açık", "Pro открыт на 24 ч"],
 "about": ["About", "Информация", "Info", "À propos", "Acerca de", "Hakkında", "О приложении"],
 "contact": ["Contact", "Контакт", "Kontakt", "Contact", "Contacto", "İletişim", "Контакты"],
 "privacy_policy": ["Privacy policy", "Политика за поверителност", "Datenschutzerklärung", "Politique de confidentialité", "Política de privacidad", "Gizlilik politikası", "Политика конфиденциальности"],
 "privacy_options": ["Ad privacy settings", "Настройки за поверителност на рекламите", "Datenschutzeinstellungen für Werbung", "Paramètres de confidentialité des annonces", "Configuración de privacidad de anuncios", "Reklam gizlilik ayarları", "Настройки конфиденциальности рекламы"],
 "no_ads_note": ["No ads before or during a calculation. Ever.", "Без реклами преди или по време на изчисление. Никога.", "Keine Werbung vor oder während einer Berechnung. Niemals.", "Aucune pub avant ou pendant un calcul. Jamais.", "Sin anuncios antes ni durante un cálculo. Nunca.", "Hesaplama öncesinde veya sırasında asla reklam yok.", "Никакой рекламы до или во время расчёта. Никогда."],
}

IOS_DIR = {"en": "en", "bg": "bg", "de": "de", "fr": "fr", "es": "es", "tr": "tr", "ru": "ru"}


def ios_val(v):
    v = v.replace("\\", "\\\\").replace('"', '\\"')
    for i in range(3):
        v = v.replace("{%d}" % i, "%@")
    return v


def android_val(v):
    v = v.replace("\\", "\\\\").replace("'", "\\'").replace('"', '\\"').replace("&", "&amp;").replace("<", "&lt;")
    for i in range(3):
        v = v.replace("{%d}" % i, "%%%d$s" % (i + 1))
    if v.startswith("@") or v.startswith("?"):
        v = "\\" + v
    return v


def write(root):
    for li, lang in enumerate(LANGS):
        d = os.path.join(root, "ios/WeightKit/Resources/%s.lproj" % IOS_DIR[lang])
        os.makedirs(d, exist_ok=True)
        with open(os.path.join(d, "Localizable.strings"), "w", encoding="utf-8") as fh:
            for k, vals in S.items():
                fh.write('"%s" = "%s";\n' % (k, ios_val(vals[li])))
        folder = "values" if lang == "en" else "values-%s" % lang
        d = os.path.join(root, "android/app/src/main/res", folder)
        os.makedirs(d, exist_ok=True)
        with open(os.path.join(d, "strings.xml"), "w", encoding="utf-8") as fh:
            fh.write('<?xml version="1.0" encoding="utf-8"?>\n<resources>\n')
            for k, vals in S.items():
                extra = ' translatable="false"' if (k in ("app_name", "pro_badge") and lang == "en") else ""
                if k in ("app_name", "pro_badge") and lang != "en":
                    continue
                fh.write('    <string name="%s"%s>%s</string>\n' % (k, extra, android_val(vals[li])))
            fh.write("</resources>\n")


if __name__ == "__main__":
    for k, v in S.items():
        assert len(v) == len(LANGS), k
    write(os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
    print("strings:", len(S))
