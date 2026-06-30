/**
 * Jeu de données local reproduisant la maquette, le temps que le flux de
 * connexion web (OIDC) et le branchement API live soient en place. Permet de
 * voir les écrans peuplés et fidèles. Sera remplacé par les données réelles.
 */
export type Rank = 'or' | 'argent' | 'bronze' | null;

export interface MockBook {
  id: string;
  title: string;
  author: string;
  series: string;
  genre: string;
  color: string;
  tag: string;
  rank: Rank;
  pages?: number;
  serieLabel?: string;
  year?: number;
  rating?: number;
  synopsis?: string;
}

const C = {
  sage: '#bccab2',
  lilac: '#cabdd6',
  rose: '#ddb9b3',
  blue: '#b6c6d6',
  sand: '#dccfae',
  mauve: '#cab5ad',
  teal: '#aec8c0',
  clay: '#d8b6a6',
  sky: '#bcc9d8',
  moss: '#c2caa6',
  dusk: '#b9b3c9',
};

export const RANK_COLORS: Record<'or' | 'argent' | 'bronze', string> = {
  or: '#d9b94e',
  argent: '#b3b7bf',
  bronze: '#c08a5a',
};

export const RANK_ICONS: Record<'or' | 'argent' | 'bronze', string> = {
  or: 'workspace_premium',
  argent: 'military_tech',
  bronze: 'military_tech',
};

export const BOOKS: MockBook[] = [
  { id: 'b1', title: 'Fourth Wing', author: 'Rebecca Yarros', series: 'The Empyrean', genre: 'Romantasy', color: C.sage, tag: 'ROMANTASY', rank: 'or', pages: 517, serieLabel: 'The Empyrean · T.1', year: 2023, rating: 5, synopsis: "Violet Sorrengail se destinait aux Scribes, mais sa mère, générale des armées, en décide autrement : direction le Quadrant des Cavaliers, où l'on apprend à chevaucher les dragons — ou l'on meurt en essayant." },
  { id: 'b2', title: 'Iron Flame', author: 'Rebecca Yarros', series: 'The Empyrean', genre: 'Romantasy', color: C.clay, tag: 'ROMANTASY', rank: 'or', pages: 640, serieLabel: 'The Empyrean · T.2', year: 2023, rating: 5, synopsis: "Survivre à sa première année à Basgiath n'était que le commencement. Le collège attend désormais de Violet qu'elle plie — mais elle n'a jamais bien su obéir." },
  { id: 'b3', title: 'A Court of Thorns and Roses', author: 'Sarah J. Maas', series: 'ACOTAR', genre: 'Romantasy', color: C.lilac, tag: 'ROMANTASY', rank: 'or', pages: 432, serieLabel: 'ACOTAR · T.1', year: 2015, rating: 5, synopsis: "Après avoir tué un loup dans les bois, Feyre est emmenée au-delà du Mur par une créature féerique, dans une terre immortelle qu'elle apprendra à craindre autant qu'à aimer." },
  { id: 'b4', title: 'Throne of Glass', author: 'Sarah J. Maas', series: 'Throne of Glass', genre: 'Fantasy', color: C.sand, tag: 'FANTASY', rank: 'argent', pages: 432, serieLabel: 'Throne of Glass · T.1', year: 2012, rating: 4, synopsis: "L'assassine Celaena Sardothien, prisonnière dans les mines de sel, se voit offrir sa liberté si elle devient championne du roi lors d'un tournoi mortel." },
  { id: 'b5', title: 'The Love Hypothesis', author: 'Ali Hazelwood', series: 'The Love Hypothesis', genre: 'Romance', color: C.rose, tag: 'ROMANCE', rank: 'argent', pages: 384, serieLabel: 'Standalone', year: 2021, rating: 4, synopsis: "Pour rassurer son amie, la doctorante Olive embrasse le premier homme venu — qui se révèle être le professeur le plus redouté du département." },
  { id: 'b6', title: 'It Ends with Us', author: 'Colleen Hoover', series: 'It Ends with Us', genre: 'Romance', color: C.blue, tag: 'ROMANCE', rank: 'argent', pages: 384, serieLabel: 'Standalone', year: 2016, rating: 4, synopsis: "Lily reconstruit sa vie à Boston quand elle rencontre Ryle, charmant neurochirurgien. Mais le retour de son premier amour fait remonter un passé qu'elle croyait enfoui." },
  { id: 'b7', title: 'Powerless', author: 'Lauren Roberts', series: 'The Powerless Trilogy', genre: 'Fantasy YA', color: C.moss, tag: 'FANTASY', rank: 'bronze', pages: 528, serieLabel: 'The Powerless · T.1', year: 2023, rating: 4, synopsis: "Dans un royaume où seuls les Élus dotés de pouvoirs survivent, Paedyn, simple humaine, cache sa nature et se retrouve plongée dans des jeux mortels." },
  { id: 'b8', title: 'Haunting Adeline', author: 'H. D. Carlton', series: 'Cat and Mouse Duet', genre: 'Dark Romance', color: C.mauve, tag: 'DARK ROM.', rank: 'bronze', pages: 482, serieLabel: 'Cat and Mouse · T.1', year: 2021, rating: 3, synopsis: "Un titre de votre collection." },
];

export const MANGAS: MockBook[] = [
  { id: 'm1', title: 'One Piece', author: 'Eiichiro Oda', series: 'One Piece', genre: 'Shōnen · Aventure', color: C.sky, tag: 'T.105', rank: 'or', pages: 208, serieLabel: 'Tome 105', year: 2024, rating: 5, synopsis: "Monkey D. Luffy et son équipage sillonnent les mers à la recherche du trésor légendaire, le One Piece." },
  { id: 'm2', title: 'One Piece', author: 'Eiichiro Oda', series: 'One Piece', genre: 'Shōnen · Aventure', color: C.sky, tag: 'T.106', rank: 'or', pages: 208, serieLabel: 'Tome 106', year: 2024, rating: 5, synopsis: "La suite des aventures de l'équipage du Chapeau de paille." },
  { id: 'm3', title: 'One Piece', author: 'Eiichiro Oda', series: 'One Piece', genre: 'Shōnen · Aventure', color: C.sky, tag: 'T.107', rank: 'or', pages: 208, serieLabel: 'Tome 107', year: 2024, rating: 5, synopsis: "La suite des aventures de l'équipage du Chapeau de paille." },
  { id: 'm4', title: 'Jujutsu Kaisen', author: 'Gege Akutami', series: 'Jujutsu Kaisen', genre: 'Shōnen · Dark fantasy', color: C.dusk, tag: 'T.24', rank: 'or', pages: 192, serieLabel: 'Tome 24', year: 2024, rating: 5, synopsis: "Pour sauver ses camarades, Yuji Itadori avale un objet maudit et devient l'hôte d'un fléau millénaire." },
  { id: 'm5', title: 'Chainsaw Man', author: 'Tatsuki Fujimoto', series: 'Chainsaw Man', genre: 'Shōnen · Action', color: C.clay, tag: 'T.15', rank: 'argent', pages: 200, serieLabel: 'Tome 15', year: 2024, rating: 5, synopsis: "Denji fusionne avec son démon-tronçonneuse Pochita et devient Chainsaw Man." },
  { id: 'm6', title: 'Spy × Family', author: 'Tatsuya Endo', series: 'Spy × Family', genre: 'Shōnen · Comédie', color: C.sage, tag: 'T.12', rank: 'or', pages: 192, serieLabel: 'Tome 12', year: 2024, rating: 5, synopsis: "Un espion, une tueuse à gages et une fillette télépathe forment une fausse famille pour une mission de paix." },
  { id: 'm7', title: 'Demon Slayer', author: 'Koyoharu Gotouge', series: 'Demon Slayer', genre: 'Shōnen · Dark fantasy', color: C.rose, tag: 'T.23', rank: 'argent', pages: 192, serieLabel: 'Tome 23', year: 2021, rating: 4, synopsis: "Après le massacre de sa famille, Tanjiro devient pourfendeur de démons pour venger les siens." },
  { id: 'm8', title: 'Blue Lock', author: 'Muneyuki Kaneshiro', series: 'Blue Lock', genre: 'Shōnen · Sport', color: C.blue, tag: 'T.25', rank: 'bronze', pages: 192, serieLabel: 'Tome 25', year: 2024, rating: 4, synopsis: "Trois cents attaquants sont enfermés dans un centre d'entraînement radical visant à forger le buteur le plus égoïste du Japon." },
];

export function findBook(id: string): MockBook | undefined {
  return [...BOOKS, ...MANGAS].find((b) => b.id === id);
}
