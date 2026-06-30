/**
 * Généré automatiquement par orval depuis openapi/openapi.json.
 * NE PAS modifier à la main — lancer `pnpm gen:api`.
 */
export interface BookView {
  editionId?: Uuid;
  kind?: string;
  title?: string;
  authors?: string;
  seriesTitle?: string;
  volumeNumber?: number;
  coverUrl?: string;
  pageCount?: number;
  publisher?: string;
  language?: string;
  isbn13?: string;
  originalYear?: number;
  synopsis?: string;
  genres?: string;
}

export interface GoalDto {
  id?: Uuid;
  year?: number;
  targetCount?: number;
  unit?: string;
}

export type GoalUnit = typeof GoalUnit[keyof typeof GoalUnit];


// eslint-disable-next-line @typescript-eslint/no-redeclare
export const GoalUnit = {
  BOOKS: 'BOOKS',
  VOLUMES: 'VOLUMES',
  PAGES: 'PAGES',
} as const;

export interface GoalUpsertDto {
  /** @minimum 1 */
  targetCount: number;
  unit?: GoalUnit;
}

export type Kind = typeof Kind[keyof typeof Kind];


// eslint-disable-next-line @typescript-eslint/no-redeclare
export const Kind = {
  BOOK: 'BOOK',
  MANGA: 'MANGA',
} as const;

export interface LibraryCreateDto {
  book: ManualBookDto;
  status?: LibraryStatus;
  rating?: number;
  acquiredAt?: LocalDate;
}

export interface LibraryItemDto {
  id?: Uuid;
  status?: string;
  rating?: number;
  acquiredAt?: LocalDate;
  book?: BookView;
}

export type LibraryStatus = typeof LibraryStatus[keyof typeof LibraryStatus];


// eslint-disable-next-line @typescript-eslint/no-redeclare
export const LibraryStatus = {
  OWNED: 'OWNED',
  READING: 'READING',
  READ: 'READ',
} as const;

export type LocalDate = string;

export interface ManualBookDto {
  kind: Kind;
  /** @pattern \S */
  title: string;
  authors?: string;
  seriesTitle?: string;
  volumeNumber?: number;
  isbn13?: string;
  publisher?: string;
  language?: string;
  pageCount?: number;
  coverUrl?: string;
  format?: string;
  releaseDate?: LocalDate;
  originalYear?: number;
  synopsis?: string;
  genres?: string;
}

export interface MeDto {
  id?: string;
  email?: string;
  displayName?: string;
  locale?: string;
}

/**
 * @pattern [a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}
 */
export type Uuid = string;

export type WishPriority = typeof WishPriority[keyof typeof WishPriority];


// eslint-disable-next-line @typescript-eslint/no-redeclare
export const WishPriority = {
  PRIORITY: 'PRIORITY',
  SOON: 'SOON',
  SOMEDAY: 'SOMEDAY',
} as const;

export interface WishlistCreateDto {
  book: ManualBookDto;
  priority?: WishPriority;
  estimatedPrice?: number;
  note?: string;
}

export interface WishlistItemDto {
  id?: Uuid;
  priority?: string;
  estimatedPrice?: number;
  note?: string;
  book?: BookView;
}

export type GetApiHello200 = {[key: string]: unknown};

export type GetApiLibraryParams = {
status?: LibraryStatus;
};

export type getApiGoalsResponse200 = {
  data: GoalDto[]
  status: 200
}

export type getApiGoalsResponse401 = {
  data: void
  status: 401
}

export type getApiGoalsResponse403 = {
  data: void
  status: 403
}
    
export type getApiGoalsResponseSuccess = (getApiGoalsResponse200) & {
  headers: Headers;
};
export type getApiGoalsResponseError = (getApiGoalsResponse401 | getApiGoalsResponse403) & {
  headers: Headers;
};

export type getApiGoalsResponse = (getApiGoalsResponseSuccess | getApiGoalsResponseError)

export const getGetApiGoalsUrl = () => {


  

  return `/api/goals`
}

export const getApiGoals = async ( options?: RequestInit): Promise<getApiGoalsResponse> => {
  
  const res = await fetch(getGetApiGoalsUrl(),
  {      
    ...options,
    method: 'GET'
    
    
  }
)

  const body = [204, 205, 304].includes(res.status) ? null : await res.text();
  
  const data: getApiGoalsResponse['data'] = body ? JSON.parse(body) : {}
  return { data, status: res.status, headers: res.headers } as getApiGoalsResponse
}



export type putApiGoalsYearResponse200 = {
  data: GoalDto
  status: 200
}

export type putApiGoalsYearResponse401 = {
  data: void
  status: 401
}

export type putApiGoalsYearResponse403 = {
  data: void
  status: 403
}
    
export type putApiGoalsYearResponseSuccess = (putApiGoalsYearResponse200) & {
  headers: Headers;
};
export type putApiGoalsYearResponseError = (putApiGoalsYearResponse401 | putApiGoalsYearResponse403) & {
  headers: Headers;
};

export type putApiGoalsYearResponse = (putApiGoalsYearResponseSuccess | putApiGoalsYearResponseError)

export const getPutApiGoalsYearUrl = (year: number,) => {


  

  return `/api/goals/${year}`
}

export const putApiGoalsYear = async (year: number,
    goalUpsertDto: GoalUpsertDto, options?: RequestInit): Promise<putApiGoalsYearResponse> => {
  
  const res = await fetch(getPutApiGoalsYearUrl(year),
  {      
    ...options,
    method: 'PUT',
    headers: { 'Content-Type': 'application/json', ...options?.headers },
    body: JSON.stringify(
      goalUpsertDto,)
  }
)

  const body = [204, 205, 304].includes(res.status) ? null : await res.text();
  
  const data: putApiGoalsYearResponse['data'] = body ? JSON.parse(body) : {}
  return { data, status: res.status, headers: res.headers } as putApiGoalsYearResponse
}



export type getApiHelloResponse200 = {
  data: GetApiHello200
  status: 200
}
    
export type getApiHelloResponseSuccess = (getApiHelloResponse200) & {
  headers: Headers;
};
;

export type getApiHelloResponse = (getApiHelloResponseSuccess)

export const getGetApiHelloUrl = () => {


  

  return `/api/hello`
}

export const getApiHello = async ( options?: RequestInit): Promise<getApiHelloResponse> => {
  
  const res = await fetch(getGetApiHelloUrl(),
  {      
    ...options,
    method: 'GET'
    
    
  }
)

  const body = [204, 205, 304].includes(res.status) ? null : await res.text();
  
  const data: getApiHelloResponse['data'] = body ? JSON.parse(body) : {}
  return { data, status: res.status, headers: res.headers } as getApiHelloResponse
}



export type getApiLibraryResponse200 = {
  data: LibraryItemDto[]
  status: 200
}

export type getApiLibraryResponse401 = {
  data: void
  status: 401
}

export type getApiLibraryResponse403 = {
  data: void
  status: 403
}
    
export type getApiLibraryResponseSuccess = (getApiLibraryResponse200) & {
  headers: Headers;
};
export type getApiLibraryResponseError = (getApiLibraryResponse401 | getApiLibraryResponse403) & {
  headers: Headers;
};

export type getApiLibraryResponse = (getApiLibraryResponseSuccess | getApiLibraryResponseError)

export const getGetApiLibraryUrl = (params?: GetApiLibraryParams,) => {
  const normalizedParams = new URLSearchParams();

  Object.entries(params || {}).forEach(([key, value]) => {
    
    if (value !== undefined) {
      normalizedParams.append(key, value === null ? 'null' : value.toString())
    }
  });

  const stringifiedParams = normalizedParams.toString();

  return stringifiedParams.length > 0 ? `/api/library?${stringifiedParams}` : `/api/library`
}

export const getApiLibrary = async (params?: GetApiLibraryParams, options?: RequestInit): Promise<getApiLibraryResponse> => {
  
  const res = await fetch(getGetApiLibraryUrl(params),
  {      
    ...options,
    method: 'GET'
    
    
  }
)

  const body = [204, 205, 304].includes(res.status) ? null : await res.text();
  
  const data: getApiLibraryResponse['data'] = body ? JSON.parse(body) : {}
  return { data, status: res.status, headers: res.headers } as getApiLibraryResponse
}



export type postApiLibraryResponse200 = {
  data: void
  status: 200
}

export type postApiLibraryResponse401 = {
  data: void
  status: 401
}

export type postApiLibraryResponse403 = {
  data: void
  status: 403
}
    
export type postApiLibraryResponseSuccess = (postApiLibraryResponse200) & {
  headers: Headers;
};
export type postApiLibraryResponseError = (postApiLibraryResponse401 | postApiLibraryResponse403) & {
  headers: Headers;
};

export type postApiLibraryResponse = (postApiLibraryResponseSuccess | postApiLibraryResponseError)

export const getPostApiLibraryUrl = () => {


  

  return `/api/library`
}

export const postApiLibrary = async (libraryCreateDto: LibraryCreateDto, options?: RequestInit): Promise<postApiLibraryResponse> => {
  
  const res = await fetch(getPostApiLibraryUrl(),
  {      
    ...options,
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...options?.headers },
    body: JSON.stringify(
      libraryCreateDto,)
  }
)

  const body = [204, 205, 304].includes(res.status) ? null : await res.text();
  
  const data: postApiLibraryResponse['data'] = body ? JSON.parse(body) : {}
  return { data, status: res.status, headers: res.headers } as postApiLibraryResponse
}



export type deleteApiLibraryIdResponse200 = {
  data: void
  status: 200
}

export type deleteApiLibraryIdResponse401 = {
  data: void
  status: 401
}

export type deleteApiLibraryIdResponse403 = {
  data: void
  status: 403
}
    
export type deleteApiLibraryIdResponseSuccess = (deleteApiLibraryIdResponse200) & {
  headers: Headers;
};
export type deleteApiLibraryIdResponseError = (deleteApiLibraryIdResponse401 | deleteApiLibraryIdResponse403) & {
  headers: Headers;
};

export type deleteApiLibraryIdResponse = (deleteApiLibraryIdResponseSuccess | deleteApiLibraryIdResponseError)

export const getDeleteApiLibraryIdUrl = (id: Uuid,) => {


  

  return `/api/library/${id}`
}

export const deleteApiLibraryId = async (id: Uuid, options?: RequestInit): Promise<deleteApiLibraryIdResponse> => {
  
  const res = await fetch(getDeleteApiLibraryIdUrl(id),
  {      
    ...options,
    method: 'DELETE'
    
    
  }
)

  const body = [204, 205, 304].includes(res.status) ? null : await res.text();
  
  const data: deleteApiLibraryIdResponse['data'] = body ? JSON.parse(body) : {}
  return { data, status: res.status, headers: res.headers } as deleteApiLibraryIdResponse
}



export type getApiMeResponse200 = {
  data: MeDto
  status: 200
}

export type getApiMeResponse401 = {
  data: void
  status: 401
}

export type getApiMeResponse403 = {
  data: void
  status: 403
}
    
export type getApiMeResponseSuccess = (getApiMeResponse200) & {
  headers: Headers;
};
export type getApiMeResponseError = (getApiMeResponse401 | getApiMeResponse403) & {
  headers: Headers;
};

export type getApiMeResponse = (getApiMeResponseSuccess | getApiMeResponseError)

export const getGetApiMeUrl = () => {


  

  return `/api/me`
}

export const getApiMe = async ( options?: RequestInit): Promise<getApiMeResponse> => {
  
  const res = await fetch(getGetApiMeUrl(),
  {      
    ...options,
    method: 'GET'
    
    
  }
)

  const body = [204, 205, 304].includes(res.status) ? null : await res.text();
  
  const data: getApiMeResponse['data'] = body ? JSON.parse(body) : {}
  return { data, status: res.status, headers: res.headers } as getApiMeResponse
}



export type getApiWishlistResponse200 = {
  data: WishlistItemDto[]
  status: 200
}

export type getApiWishlistResponse401 = {
  data: void
  status: 401
}

export type getApiWishlistResponse403 = {
  data: void
  status: 403
}
    
export type getApiWishlistResponseSuccess = (getApiWishlistResponse200) & {
  headers: Headers;
};
export type getApiWishlistResponseError = (getApiWishlistResponse401 | getApiWishlistResponse403) & {
  headers: Headers;
};

export type getApiWishlistResponse = (getApiWishlistResponseSuccess | getApiWishlistResponseError)

export const getGetApiWishlistUrl = () => {


  

  return `/api/wishlist`
}

export const getApiWishlist = async ( options?: RequestInit): Promise<getApiWishlistResponse> => {
  
  const res = await fetch(getGetApiWishlistUrl(),
  {      
    ...options,
    method: 'GET'
    
    
  }
)

  const body = [204, 205, 304].includes(res.status) ? null : await res.text();
  
  const data: getApiWishlistResponse['data'] = body ? JSON.parse(body) : {}
  return { data, status: res.status, headers: res.headers } as getApiWishlistResponse
}



export type postApiWishlistResponse200 = {
  data: void
  status: 200
}

export type postApiWishlistResponse401 = {
  data: void
  status: 401
}

export type postApiWishlistResponse403 = {
  data: void
  status: 403
}
    
export type postApiWishlistResponseSuccess = (postApiWishlistResponse200) & {
  headers: Headers;
};
export type postApiWishlistResponseError = (postApiWishlistResponse401 | postApiWishlistResponse403) & {
  headers: Headers;
};

export type postApiWishlistResponse = (postApiWishlistResponseSuccess | postApiWishlistResponseError)

export const getPostApiWishlistUrl = () => {


  

  return `/api/wishlist`
}

export const postApiWishlist = async (wishlistCreateDto: WishlistCreateDto, options?: RequestInit): Promise<postApiWishlistResponse> => {
  
  const res = await fetch(getPostApiWishlistUrl(),
  {      
    ...options,
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...options?.headers },
    body: JSON.stringify(
      wishlistCreateDto,)
  }
)

  const body = [204, 205, 304].includes(res.status) ? null : await res.text();
  
  const data: postApiWishlistResponse['data'] = body ? JSON.parse(body) : {}
  return { data, status: res.status, headers: res.headers } as postApiWishlistResponse
}



export type deleteApiWishlistIdResponse200 = {
  data: void
  status: 200
}

export type deleteApiWishlistIdResponse401 = {
  data: void
  status: 401
}

export type deleteApiWishlistIdResponse403 = {
  data: void
  status: 403
}
    
export type deleteApiWishlistIdResponseSuccess = (deleteApiWishlistIdResponse200) & {
  headers: Headers;
};
export type deleteApiWishlistIdResponseError = (deleteApiWishlistIdResponse401 | deleteApiWishlistIdResponse403) & {
  headers: Headers;
};

export type deleteApiWishlistIdResponse = (deleteApiWishlistIdResponseSuccess | deleteApiWishlistIdResponseError)

export const getDeleteApiWishlistIdUrl = (id: Uuid,) => {


  

  return `/api/wishlist/${id}`
}

export const deleteApiWishlistId = async (id: Uuid, options?: RequestInit): Promise<deleteApiWishlistIdResponse> => {
  
  const res = await fetch(getDeleteApiWishlistIdUrl(id),
  {      
    ...options,
    method: 'DELETE'
    
    
  }
)

  const body = [204, 205, 304].includes(res.status) ? null : await res.text();
  
  const data: deleteApiWishlistIdResponse['data'] = body ? JSON.parse(body) : {}
  return { data, status: res.status, headers: res.headers } as deleteApiWishlistIdResponse
}
