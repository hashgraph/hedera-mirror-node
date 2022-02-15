function isNonErrorResponse(response){
  //instead of doing multiple type checks,
  //lets just do the normal path and return false,
  //if an exception happens.
  try{
    const body = JSON.parse(response.body);
    return body["error"] === undefined && body["error_code"] === undefined;
  }
  catch(e){
    console.log(e);
    return false;
  }
}

export {isNonErrorResponse};
